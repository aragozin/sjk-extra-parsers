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

import java.util.ServiceLoader;

import org.gridkit.jvmtool.event.EventDumpParser;
import org.gridkit.jvmtool.jfr.JfrDumpParserLoader;
import org.junit.Assert;
import org.junit.Test;

public class ServiceLoaderTest {

	@Test	
	public void test_load() {
		
		ServiceLoader<EventDumpParser> loader = ServiceLoader.load(EventDumpParser.class);
		boolean found = false;
		for(EventDumpParser edp: loader) {
			if (edp instanceof JfrDumpParserLoader) {
				found = true;
			}
		}
		Assert.assertTrue("JfrDumpParserLoader should be listed", found);
	}
}
