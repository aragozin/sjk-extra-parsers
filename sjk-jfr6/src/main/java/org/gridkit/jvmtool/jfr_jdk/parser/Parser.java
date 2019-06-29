package org.gridkit.jvmtool.jfr_jdk.parser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.gridkit.jvmtool.spi.parsers.FileInputStreamSource;
import org.gridkit.jvmtool.spi.parsers.InputStreamSource;
import org.gridkit.jvmtool.spi.parsers.JsonEventDumpParser;
import org.gridkit.jvmtool.spi.parsers.JsonEventSource;

import jdk.jfr.consumer.RecordingFile;

class Parser implements JsonEventDumpParser {
	
	private int jsonMaxDepth = Integer.MAX_VALUE;
	private List<String> whiteList = null;
	private List<String> blackList = null;

	private static void checkClasses() {
		try {
			RecordingFile.class.getSimpleName();
		}
		catch(Throwable e) {
			throw new RuntimeException("Native JFR parser is not available");
		}
	}

	public Parser() {
		checkClasses();		
	}
	
	@Override
	public JsonEventSource open(InputStreamSource source) throws Exception {
		
		if (source instanceof FileInputStreamSource) {
			File file = ((FileInputStreamSource) source).getSourceFile();

			RecordingFile rec = new RecordingFile(file.toPath());
			
			EventSource eventSource = new EventSource(rec, jsonMaxDepth);
			if (whiteList != null) {
				eventSource.setWhiteList(whiteList);
			}
			if (blackList != null) {
				eventSource.setBlackList(blackList);
			}

			return eventSource;

		}
		else {
			throw new IOException("Native JFR parser is rescripted to file system IO");
		}
	}

	public void configure(Map<String, String> options) {
		for(String key: options.keySet()) {
			if (JsonFlightRecordingParserFactory.OPT_USE_NATIVE_JFR_PARSER.equals(key)) {
				if ("false".equalsIgnoreCase(options.get(key))) {
					throw new RuntimeException("Unsatisfied option: " + key + "=" + options.get(key));
				}
			}
			else if (JsonFlightRecordingParserFactory.OPT_JSON_MAX_DEPTH.equals(key)) {
				jsonMaxDepth = Integer.valueOf(options.get(key));
				if (jsonMaxDepth < 0) {
					throw new RuntimeException("Illegal option value: " + key + "=" + options.get(key));
				}
			}
			else if (JsonFlightRecordingParserFactory.OPT_JFR_EVENT_BLACKLIST.equals(key)) {
				String[] list = options.get(key).split(",");
				if (list.length > 0) {
					blackList = Arrays.asList(list);
				}
			}
			else if (JsonFlightRecordingParserFactory.OPT_JFR_EVENT_WHITELIST.equals(key)) {
				String[] list = options.get(key).split(",");
				if (list.length > 0) {
					whiteList = Arrays.asList(list);
				}
			}
			
			else {
				throw new RuntimeException("Unknown option: " + key + "=" + options.get(key));
			}
		}
	}
}