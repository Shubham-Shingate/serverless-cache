package com.serverless.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.Function;
import com.google.gson.JsonObject;

public class MFUCache {

	private static int INIT_CAPACITY;
	
	private static int pf;
	
	private static Map<Object, Object> cache;
	
	private static Map<Object, Integer> freqTracker;
	
	static {
		INIT_CAPACITY = 10;
		cache =  new LinkedHashMap<Object, Object>(INIT_CAPACITY);
		freqTracker = new LinkedHashMap<Object, Integer>(INIT_CAPACITY);
	}
	
	//Entry point for the openwhisk java runtime (OWK JVM)
	public static JsonObject main(JsonObject args) throws Exception {
		
		Function<String, String> func = jsonKey -> args.getAsJsonPrimitive(jsonKey).getAsString();
	
		String cmd = func.apply("cmd");
		String key = null;
		JsonObject response = new JsonObject();
		
		switch (cmd) {
		case "put":
			key = func.apply("key");
			String value = func.apply("value");
			put(Integer.parseInt(key), value);
			break;

		case "get":
			key = func.apply("key");
			String val = (String) get(Integer.parseInt(key));
			response.addProperty(key, val);
			response.addProperty("pf", pf);
			break;
			
		default:
			Map<Object, Object> cacheData = display();
			Set<Entry<Object, Object>> cacheDataEntrys = cacheData.entrySet();
			
			JsonObject cacheDataJson = new JsonObject();
			for (Entry<Object, Object> entry : cacheDataEntrys) {
				cacheDataJson.addProperty(entry.getKey().toString(), entry.getValue().toString());
			}
			response.add("cacheData", cacheDataJson);
			break;
		}
		
		JsonObject headers = new JsonObject();
	    headers.addProperty("content-type", "application/json");
		response.add("headers", headers);
		return response;
	}

	// PUT <key, value> operation in the LRU cache
	public static void put(Object key, Object value) {
		BiPredicate<Integer, Integer> p = (size, capacity) -> (size < capacity) ? true : false;
		
		Integer result = freqTracker.computeIfPresent(key, (k, oldVal) -> {
			cache.put(key, value);
			return oldVal + 1;
		});
		
		if (result == null) { //Means this is a new entry record.
			if (p.test(cache.size(), INIT_CAPACITY)) {
				freqTracker.put(key, 1);
				cache.put(key, value);
			} else { //Run LFU eviction policy
				Integer maxkey = 0;
				Integer lastMax = 0;
				Set<Entry<Object, Integer>> entries = freqTracker.entrySet();
				for (Entry<Object, Integer> entry : entries) {
					if (entry.getValue() > lastMax) {
						lastMax = entry.getValue();
						maxkey = (Integer) entry.getKey();
					}
				}
				freqTracker.remove(maxkey);
				cache.remove(maxkey);
				cache.put(key, value);
				freqTracker.put(key, 1);
			}
		}
	}
	
	// GET an object from cache and update it position as per LRU order
	public static Object get(Object key) {
		if (cache.containsKey(key)) {
			freqTracker.compute(key, (k, oldVal) -> oldVal+1);
			return cache.get(key);
		} else {
			pf++;
			return null;
		}
	}

	// Display all the elements of a cache in LRU order.
	public static Map<Object, Object> display() {
		return cache;
	}

}
