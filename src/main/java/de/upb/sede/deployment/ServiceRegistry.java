package de.upb.sede.deployment;

import java.util.Collection;

import org.json.simple.parser.ParseException;

import de.upb.sede.webinterfaces.server.BasicServerResponse;

public class ServiceRegistry {
	BasicServerResponse responseToExecutionStarter;
	ServiceInventory inventory;

	public ServiceRegistry(Collection<ServiceAssemblyLoad> serviceAssemblyLoadRequests) throws ParseException {
		inventory.loadServices(serviceAssemblyLoadRequests);
	}
}