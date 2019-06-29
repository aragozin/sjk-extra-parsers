/**
 * Copyright 2017 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.jvmtool.jfr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import org.gridkit.jvmtool.event.EventDumpParser.InputStreamSource;
import org.junit.Test;

import com.oracle.jmc.flightrecorder.CouldNotLoadRecordingException;
import com.oracle.jmc.flightrecorder.internal.EventArray;
import com.oracle.jmc.flightrecorder.internal.FlightRecordingLoader;

public class JfrParserCheck {

	private InputStreamSource source = new InputStreamSource() {
		
		public InputStream open() throws IOException {
			String jfr = "C:/fire_at_will/abc.jfr";
			File file = new File(jfr);
			return new GZIPInputStream(new FileInputStream(file));
		}
	};
	
	@Test
	public void go() throws IOException, CouldNotLoadRecordingException {
		
		EventArray[] recording = FlightRecordingLoader.loadStream(source.open(), false);
		for(EventArray ea: recording) {
			System.out.println(Arrays.asList(ea.getTypeCategory()) + " - " + ea.getEvents().length + " - " + ea.getType().getIdentifier());
//			if (ea.getEvents().length > 0 && ea.getType().getIdentifier().equals("com.oracle.jdk.ObjectAllocationInNewTLAB")) {
//				IItem e = ea.getEvents()[1];
//				JfrAttributes.EVENT_TIMESTAMP.getAccessor(ea.getType()).getMember(e);
//				JfrAttributes.EVENT_THREAD.getAccessor(ea.getType()).getMember(e);
//				JfrAttributes.EVENT_STACKTRACE.getAccessor(ea.getType()).getMember(e);
//				e.getType().getAttributes().get(4).getAccessor(ea.getType()).getMember(e);
//			}
		}
	}	
}
