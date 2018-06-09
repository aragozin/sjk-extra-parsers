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
package org.gridkit.jvmtool.jfr.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.gridkit.jvmtool.event.ErrorEvent;
import org.gridkit.jvmtool.event.Event;
import org.gridkit.jvmtool.event.EventDumpParser;
import org.gridkit.jvmtool.event.EventReader;

import com.jrockit.mc.flightrecorder.FlightRecording;
import com.jrockit.mc.flightrecorder.FlightRecordingLoader;

public class JfrDumpParser implements EventDumpParser {

	public boolean isFunctional() {
		return true;
	}
	
	public JfrDumpParser() {
		try {
			// force class loading
			FlightRecordingLoader.loadStream(new ByteArrayInputStream(new byte[0]));
		}
		catch(Exception e) {
			// ignore
		}
		catch(Error e) {
			// ignore
		}
	}
	
	@Override
	public EventReader<Event> open(InputStreamSource source) throws IOException {
 
		try {
			InputStream is = null;
			if (isGZip(source)) {
				is = new GZIPInputStream(source.open());
			}
			else {
				is = source.open();
			}
			
			FlightRecording frl = FlightRecordingLoader.loadStream(is);
			
			EventReader<Event> adapter = new JfrEventAdapter(frl);
			if (!adapter.hasNext() || adapter.hasNext() && adapter.peekNext() instanceof ErrorEvent) {
				// cannot read file
				return null;
			}
			
			return adapter;
		}
		catch(NoSuchMethodError e) {
			return null;
		}
		catch(NoClassDefFoundError e) {
			return null;
		}
	}

	private boolean isGZip(InputStreamSource source) {
		
		try {
			GZIPInputStream gzi = new GZIPInputStream(source.open());
			gzi.read();
			return true;
		}
		catch(IOException e) {
		}
		
		return false;
	}
}
