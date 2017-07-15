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
package org.gridkit.jvmtool.nps;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.gridkit.jvmtool.event.EventDumpParser;

public class NetbeansSnapshotParserBootstrapper {

	private static final String PARSER_PACKAGE = "org.gridkit.jvmtool.nps.parser";
	private static final String PARSER_CLASS = PARSER_PACKAGE + ".NetbeansSnapshotParser";
	
	List<URL> visualvmPath = new ArrayList<URL>();
	
	public EventDumpParser load() {

		String javaHome = System.getProperty("java.home");
		File f = new File(javaHome);		

		scanDir(path(f, "lib/visualvm/profiler/modules"));
		scanDir(path(f, "lib/visualvm/platform/lib"));
		
		if (javaHome.endsWith("jre")) {
			f = new File(javaHome.substring(0, javaHome.length() - 4));
			scanDir(path(f, "lib/visualvm/profiler/modules"));
			scanDir(path(f, "lib/visualvm/platform/lib"));
		}
		
		if (visualvmPath.isEmpty()) {
			return null;
		}
		
		visualvmPath.addAll(Arrays.asList(((URLClassLoader)this.getClass().getClassLoader()).getURLs()));
		
		NbClassLoader cl = new NbClassLoader(visualvmPath.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
		
		try {
			return new Instantiator(cl).call();
		}
		catch(Throwable e) {
			// ignore
		}
		
		try {
			return new Instantiator(Thread.currentThread().getContextClassLoader()).call();
		}
		catch(Throwable e) {
			// ignore
		}
		
		return null;
	}

	private void scanDir(File path) {
		if (path.isDirectory() && path.listFiles() != null) {
			for(File j: path.listFiles()) {
				matchFile(j, "org-netbeans-modules-profiler.jar", "");
				matchFile(j, "org-netbeans-lib-profiler.jar", "");
				matchFile(j, "org-netbeans-lib-profiler-common.jar", "");
				matchFile(j, "org-openide-util.jar", "");
				matchFile(j, "org-openide-util-lookup.jar", "");
			}
		}
	}

	private void matchFile(File j, String s, String e) {
		try {
			if (j.isFile() && j.getName().startsWith(s) && j.getName().endsWith(e)) {
				visualvmPath.add(j.toURI().toURL());
			}
		} catch (MalformedURLException ee) {
			// ignore
		}
	}

	private File path(File f, String path) {
		File ff = f;
		for(String s: path.split("[/]")) {
			ff = new File(ff, s);
		}
		return ff;
	}

	static class NbClassLoader extends URLClassLoader {

		private final ClassLoader baseClassloader;
		
		public NbClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, null);
			baseClassloader = parent;
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			if (shouldLoad(name)) {
				String bytepath = name.replace('.', '/') + ".class";
				URL url = getResource(bytepath);
				if (url == null) {
					throw new ClassNotFoundException(name);
				}
				Class<?> cl = findLoadedClass(name);
				if (cl == null) {
					cl = findClass(name);
				}
				if (cl == null) {
					throw new ClassNotFoundException(name);
				}					
				return cl;				
			}
			Class<?> cc = baseClassloader.loadClass(name);
			return cc;
		}

		private boolean shouldLoad(String name) {
			return name.startsWith(PARSER_PACKAGE) || name.startsWith("org.netbeans");
		}
	}
	
	static class Instantiator implements Callable<EventDumpParser> {

		final ClassLoader cl;
		
		public Instantiator(ClassLoader cl) {
			this.cl = cl;
		}

		@Override
		public EventDumpParser call() throws Exception {
			return (EventDumpParser) cl.loadClass(PARSER_CLASS).newInstance();
		}
	}
}
