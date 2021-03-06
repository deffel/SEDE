package de.upb.sede.exec;

import java.util.*;

import de.upb.sede.config.ExecutorConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;

import de.upb.sede.interfaces.IExecutor;
import de.upb.sede.procedure.AcceptDataProcedure;
import de.upb.sede.procedure.CastTypeProcedure;
import de.upb.sede.procedure.InstructionProcedure;
import de.upb.sede.procedure.ParseConstantProcedure;
import de.upb.sede.procedure.ServiceInstanceStorageProcedure;
import de.upb.sede.requests.DataPutRequest;
import de.upb.sede.requests.ExecRequest;
import de.upb.sede.requests.ExecutorRegistration;
import de.upb.sede.util.Observer;
import de.upb.sede.util.Observable;

/**
 * Core implementation of an executor.
 */
public class Executor implements IExecutor {


	private static final Logger logger = LogManager.getLogger();

	private static final GraphJsonDeserializer deserializer = new GraphJsonDeserializer();

	private final ExecutionPool execPool;

	private final ExecutorConfiguration config;

	private final WorkerPool workerPool;

	private final Observer<Task> taskWorkerEnqueuer;

	private final Observer<Execution> executionGarbageCollector;

	/**
	 * Contact info map.
	 * It is sent to the gateway and used to contact this executor.
	 * Can be modified by plugins.
	 */
	private final Map<String, Object> contactInfo = new HashMap<>();

	/*
	 * Hooks of the executor:
	 * These hooks are used by plugins who need to add functionality for certain events.
	 */

	/**
	 * This hook triggers when the executor has received a shutdown command. <br>
	 * The notification item is 'this' Executor.
	 */
	public final Observable<Executor> shutdownHook = new Observable<>();

	/**
	 * This hook triggers when a new Execution has been created through an {@link this#exec} invocation.<br>
	 * The notification item is the new Execution object.
	 */
	public final Observable<Execution> newExecutionHook = new Observable<>();

	public Executor(ExecutorConfiguration execConfig) {
		this.execPool = new ExecutionPool(execConfig);
		this.config = execConfig;
		this.workerPool = new WorkerPool(execConfig.getThreadNumber());
		this.taskWorkerEnqueuer = Observer.lambda(t->true,  workerPool::processTask, t->false);
		this.executionGarbageCollector = Observer.<Execution>lambda(Execution::hasExecutionFinished,  // when an execution is done, .
				this::removeExecution);
		contactInfo.put("id", getExecutorConfiguration().getExecutorId());
		bindProcedureNames();
		logger.info("Executor with id '{}' created.\n" +
				"Capabilities: {}\n" +
				"Supported services: {}\n" +
				"Contact-information: {}",
				execConfig.getExecutorId(), capabilities(), supportedServices(), contactInfo());
	}

	private final void bindProcedureNames(){
		workerPool.bindProcedure("Instruction", InstructionProcedure::new);
		workerPool.bindProcedure("ParseConstant", ParseConstantProcedure::new);
		workerPool.bindProcedure("AcceptData", AcceptDataProcedure::new);
		workerPool.bindProcedure("CastType", CastTypeProcedure::new);
		workerPool.bindProcedure("DeleteField", null); // TODO
		workerPool.bindProcedure("ServiceInstanceStorage", ServiceInstanceStorageProcedure::new);
		// send graph and transmit data needs to be bounded from outside because
		// based on the type of this executor they require different implementations.
		// One can also rebind other procedures to change the behaviour of the executor.
	}

	public WorkerPool getWorkerPool() {
		return workerPool;
	}


	public Optional<Execution> getExecution(String execId) {
		return execPool.getExecution(execId);
	}

	public Execution getOrCreateExecution(String execId) {
		return execPool.getOrCreateExecution(execId);
	}

	public boolean execIdTaken(String execId) {
		return execPool.getOrCreateExecution(execId).hasStarted();
	}

	public ExecutionPool getExecPool() {
		return execPool;
	}

	public void removeExecution(Execution execution) {
		execPool.removeExecution(execution);
		workerPool.removeExecution(execution);
	}

	@Override
	public synchronized void put(DataPutRequest dataPutRequest){
		Execution exec = getOrCreateExecution(dataPutRequest.getRequestID());
		if(dataPutRequest.isUnavailable()) {
			/*
			 * The request indicates that the data is unavailable. (wont be delivered)
			 */
			exec.getEnvironment().markUnavailable(dataPutRequest.getFieldname());
		} else{
			/*
			 * The request contains the data:
			 */
			exec.getEnvironment().put(dataPutRequest.getFieldname(), dataPutRequest.getData());
		}
	}

	@Override
	public synchronized Execution exec(ExecRequest execRequest){
		String execId = execRequest.getRequestID();

		/* First check if execution id is taken: */
		if(execIdTaken(execId)) {
			/*
				Throw exception signaling that the exec Id is already occupied:
			 */
			throw new RuntimeException("Execution Id was already taken: " + execId);
		}
		Execution exec = getOrCreateExecution(execRequest.getRequestID());
		exec.getRunnableTasksObservable().observe(taskWorkerEnqueuer);

		deserializer.deserializeTasksInto(exec, execRequest.getCompositionGraph());

		exec.getState().observe(executionGarbageCollector);
		exec.start();
		/*
		  	Notify hooks:
		 */
		newExecutionHook.update(exec);
		logger.debug("Execution request {} started.", execRequest.getRequestID());
		return exec;
	}

	@Override
	public synchronized void interrupt(String executionId) {
		if (execPool.hasExecution(executionId)) {
			Optional<Execution> toBeInterrupted = execPool.getExecution(executionId);
			if(toBeInterrupted.isPresent()){
				Execution target = toBeInterrupted.get();
				workerPool.interruptExec(target);
				target.interrupt();
			}
		}
	}



	public void interruptAll() {
		execPool.forAll(Execution::interrupt);
	}

	public Set<String> capabilities() {
		Set<String> capabilities = new TreeSet<>();
		/*
		 * Add implementation specific capabilities:
		 */
		// This is a java executor:
		capabilities.add("java");
		// The java implementation supports casting in place in transmit and accept nodes:
		capabilities.add("cast_in_place");
		/*
		 * add capabilities specified in the configuration:
		 */
		capabilities.addAll(getExecutorConfiguration().getExecutorCapabilities());
		return capabilities;
	}

	public Set<String> supportedServices() {
		Set<String> services = new TreeSet<>();
		services.addAll(getExecutorConfiguration().getSupportedServices());
		/*
		 * add built-in services:
		 * (No built-in service.)
		 */
		return services;
	}

	public Map<String, Object> getModifiableContactInfo() {
		return contactInfo;
	}

	@Override
	public Map<String, Object> contactInfo() {
		return Collections.unmodifiableMap(contactInfo);
	}

	@Override
	public ExecutorRegistration registration() {
		List<String> capibilities = new ArrayList<>(capabilities());
		List<String> supportedServices = new ArrayList<>(supportedServices());
		Map<String, Object> contactInfo = contactInfo();
		ExecutorRegistration registration = new ExecutorRegistration
				(contactInfo, capibilities, supportedServices);
		return registration;
	}


	public ExecutorConfiguration getExecutorConfiguration() {
		return config;
	}

	@Override
	public void shutdown() {
		interruptAll();
		getWorkerPool().shutdown();
		shutdownHook.update(this);
	}

}
