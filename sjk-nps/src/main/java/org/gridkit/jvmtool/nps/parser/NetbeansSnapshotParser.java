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
package org.gridkit.jvmtool.nps.parser;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.gridkit.jvmtool.event.Event;
import org.gridkit.jvmtool.event.EventDumpParser;
import org.gridkit.jvmtool.event.EventReader;
import org.netbeans.lib.profiler.results.ResultsSnapshot;
import org.netbeans.lib.profiler.results.cpu.CPUResultsSnapshot;
import org.netbeans.modules.profiler.LoadedSnapshot;

public class NetbeansSnapshotParser implements EventDumpParser {

	public NetbeansSnapshotParser() {
		try {
			// force class loading
			open(new InputStreamSource() {
				
				@Override
				public InputStream open() throws IOException {
					return new ByteArrayInputStream(new byte[0]);
				}
			});
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

		ClassLoader contextCl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
			
			LoadedSnapshot snapshot = LoadedSnapshot.loadSnapshot(new DataInputStream(source.open()));
			ResultsSnapshot snap = snapshot.getSnapshot();
			if (snap instanceof CPUResultsSnapshot) {
				NpsEventAdapter adapter = new NpsEventAdapter((CPUResultsSnapshot) snap);
				if (!adapter.hasNext()) {
					return null;
				}				
				return adapter;
			}
		}
		catch(NoSuchMethodError e) {
			e.printStackTrace();
			// classpath problem
		}
		catch(NoClassDefFoundError e) {
			e.printStackTrace();
			// classpath problem			
		}
		finally {
			Thread.currentThread().setContextClassLoader(contextCl);
		}
		return null;
	}
}
