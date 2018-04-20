package de.upb.sede.requests.resolve;

import java.util.Map;
import java.util.Optional;

import org.json.simple.JSONObject;

import de.upb.sede.requests.Request;

public class ResolveRequest extends Request {
	private Optional<String> composition;
	private Optional<ResolvePolicy> policy;
	private Optional<InputFields> inputFields; 

	public ResolveRequest() {
		this.composition = Optional.empty();
		this.policy = Optional.empty();
		this.inputFields = Optional.empty();
	}

	public ResolveRequest(String requestId, String clientHost, String composition, ResolvePolicy policy, InputFields inputFields) {
		super(requestId, clientHost);
		this.composition = Optional.of(composition);
		this.policy = Optional.of(policy);
		this.inputFields = Optional.of(inputFields);
	}

	
	public boolean hasComposition() {
		return this.composition.isPresent();
	}

	public boolean hasPolicy() {
		return this.policy.isPresent();
	}

	public String getComposition() {
		assert hasComposition();
		return composition.get();
	}

	public ResolvePolicy getPolicy() {
		assert hasPolicy();
		return policy.get();
	}
	
	public InputFields getInputFields(){
		return inputFields.get();
	}
	

	@SuppressWarnings("unchecked")
	@Override
	public JSONObject toJson() {
		JSONObject jsonObject = super.toJson();
		jsonObject.put("composition", getComposition());
		jsonObject.put("policy", getPolicy().toJson());
		jsonObject.put("input-fields", getInputFields().toJson());
		return jsonObject;
	}

	@Override
	public void fromJson(Map<String, Object> data) {
		super.fromJson(data);
		
		composition = Optional.of((String)data.get("composition"));
		
		ResolvePolicy resolvePolicy = new ResolvePolicy();
		resolvePolicy.fromJson((Map<String, Object>) data.get("policy"));
		policy = Optional.of(resolvePolicy);
		
		InputFields jsonInputFields = new InputFields();
		jsonInputFields.fromJson((Map<String, Object>) data.get("input-fields"));
		this.inputFields = Optional.of(jsonInputFields);
	}
}
