package com.serverless.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import java.util.function.Function;
import com.google.gson.JsonObject;

public class FIFOCache {

	private static int INIT_CAPACITY;
	
	private static Map<Object, Object> cache;
	
	static {
		INIT_CAPACITY = 30;
		cache =  new LinkedHashMap<Object, Object>(INIT_CAPACITY);
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
			put(key, value);
			break;

		case "get":
			key = func.apply("key");
			String val = (String) get(key);
			response.addProperty(key, val);
			break;
			
		default:
			Map<Object, Object> cacheData = display();
			Set<Entry<Object, Object>> cacheDataEntrys = cacheData.entrySet();
			
			JsonObject cacheDataJson = new JsonObject();
			for (Entry<Object, Object> entry : cacheDataEntrys) {
				cacheDataJson.addProperty((String) entry.getKey(), (String) entry.getValue());
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

		if (p.test(cache.size(), INIT_CAPACITY)) {
			cache.put(key, value);
		} else { // RUN FIFO EVICTION POLICY
			Set<Entry<Object, Object>> mapSet = cache.entrySet();
			cache.remove(mapSet.stream().findFirst().get().getKey());
			cache.put(key, value);
		}
	}

	// GET an object from cache and update it position as per LRU order
	public static Object get(Object key) {
		if (cache.containsKey(key)) {	
			return cache.get(key);
		} else {
			return null;
		}
	}

	// Display all the elements of a cache in LRU order.
	public static Map<Object, Object> display() {
		return cache;
	}

}
