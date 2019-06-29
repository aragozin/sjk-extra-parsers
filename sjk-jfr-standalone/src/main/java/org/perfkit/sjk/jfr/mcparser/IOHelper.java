package org.perfkit.sjk.jfr.mcparser;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.gridkit.jvmtool.spi.parsers.InputStreamSource;

class IOHelper {
	
	public static InputStream openFlatOrCommpressedStream(InputStreamSource iss) throws IOException {
		
		try {
			GZIPInputStream gis = new GZIPInputStream(iss.open());
			gis.read();
			gis.close();
			return new GZIPInputStream(iss.open());
		}
		catch(IOException e) {
			// stream is probably not compressed
		}
		
		return iss.open();		
	}
}
