package org.gridkit.jvmtool.jfr_jdk.parser;

import java.util.Map;

import org.gridkit.jvmtool.spi.parsers.JsonEventDumpParser;
import org.gridkit.jvmtool.spi.parsers.JsonEventDumpParserFactory;

public class JsonFlightRecordingParserFactory implements JsonEventDumpParserFactory {	
		
	@Override
	public JsonEventDumpParser createParser(Map<String, String> options) throws Exception {
		
		Parser parser = new Parser();
		parser.configure(options);
		
		return parser;
		
	}
}
