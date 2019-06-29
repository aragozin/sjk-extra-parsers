package org.perfkit.sjk.jfr.mcparser;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.internal.EventArray;
import org.openjdk.jmc.flightrecorder.internal.IChunkLoader;
import org.openjdk.jmc.flightrecorder.internal.IChunkSupplier;
import org.openjdk.jmc.flightrecorder.internal.InvalidJfrFileException;
import org.openjdk.jmc.flightrecorder.internal.VersionNotSupportedException;
import org.openjdk.jmc.flightrecorder.internal.parser.Chunk;
import org.openjdk.jmc.flightrecorder.internal.parser.LoaderContext;
import org.openjdk.jmc.flightrecorder.internal.parser.v0.ChunkLoaderV0;
import org.openjdk.jmc.flightrecorder.internal.parser.v1.ChunkLoaderV1;
import org.openjdk.jmc.flightrecorder.parser.IParserExtension;
import org.openjdk.jmc.flightrecorder.parser.ParserExtensionRegistry;

public class JFRLoader {

	private static final Logger LOGGER = Logger.getLogger(JFRLoader.class.getName());
	private static final short VERSION_0 = 0; // JDK7 & JDK8
	private static final short VERSION_1 = 1; // JDK9 & JDK10
	private static final short VERSION_2 = 2; // JDK11
	private static final byte[] FLIGHT_RECORDER_MAGIC = {'F', 'L', 'R', '\0'};

	public static EventArray[] loadStream(InputStream stream, boolean hideExperimentals, boolean ignoreTruncatedChunk)
			throws CouldNotLoadRecordingException, IOException {
		return loadStream(stream, ParserExtensionRegistry.getParserExtensions(), hideExperimentals,
				ignoreTruncatedChunk);
	}

	/**
	 * Read events from an input stream of JFR data.
	 *
	 * @param stream
	 *            input stream
	 * @param extensions
	 *            the extensions to use when parsing the data
	 * @param hideExperimentals
	 *            if {@code true}, then events of types marked as experimental will be ignored when
	 *            reading the data
	 * @return an array of EventArrays (one event type per EventArray)
	 */
	public static EventArray[] loadStream(
		InputStream stream, List<? extends IParserExtension> extensions, boolean hideExperimentals,
		boolean ignoreTruncatedChunk) throws CouldNotLoadRecordingException, IOException {
		return readChunks(extensions, createChunkSupplier(stream), hideExperimentals, ignoreTruncatedChunk);
	}

	private static IChunkSupplier createChunkSupplier(final InputStream input)
			throws CouldNotLoadRecordingException, IOException {
		return new IChunkSupplier() {

			@Override
			public Chunk getNextChunk(byte[] reusableBuffer) throws CouldNotLoadRecordingException, IOException {
				int value = input.read();
				if (value < 0) {
					return null;
				}
				return createChunkInput(new DataInputStream(input), value, reusableBuffer);
			}
		};
	}

	private static Chunk createChunkInput(DataInput input, int firstByte, byte[] reusableBuffer)
			throws CouldNotLoadRecordingException, IOException {
		int i = 0;
		while (FLIGHT_RECORDER_MAGIC[i] == firstByte) {
			if (++i == FLIGHT_RECORDER_MAGIC.length) {
				return new Chunk(input, FLIGHT_RECORDER_MAGIC.length, reusableBuffer);
			}
			firstByte = input.readUnsignedByte();
		}
		throw new InvalidJfrFileException();
	}

	private static EventArray[] readChunks(			
			List<? extends IParserExtension> extensions, 
			IChunkSupplier chunkSupplier,
			boolean hideExperimentals, 
			boolean ignoreTruncatedChunk
		) throws CouldNotLoadRecordingException, IOException {

		LoaderContext context = new LoaderContext(extensions, hideExperimentals);

		int chunkCount = 0;
		try {
			byte[] buffer = new byte[0];
			IChunkLoader chunkLoader;
			while ((chunkLoader = createChunkLoader(chunkSupplier, context, buffer, ignoreTruncatedChunk)) != null) {
				try {
					chunkLoader.call();
				}
				catch(Exception e) {
					throw new ExecutionException(e);
				}
				++chunkCount;
			}
			if (chunkCount == 0) {
				// Recordings without any chunks are not allowed
				throw new InvalidJfrFileException("No readable chunks in recording"); //$NON-NLS-1$
			}
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Error) {
				throw ((Error) cause);
			} else if (cause instanceof RuntimeException) {
				throw ((RuntimeException) cause);
			} else if (cause instanceof IOException) {
				throw ((IOException) cause);
			} else if (cause instanceof CouldNotLoadRecordingException) {
				throw ((CouldNotLoadRecordingException) cause);
			} else {
				throw new CouldNotLoadRecordingException(cause);
			}
		}
		return context.buildEventArrays();
	}

	/**
	 * @param chunkSupplier
	 *            chunk data source
	 * @param context
	 *            loader context that the returned chunk loader will send event data to
	 * @param buffer
	 *            Initial byte array to use for storing chunk data. See
	 *            {@link IChunkSupplier#getNextChunk(byte[])}.
	 * @param ignoreTruncatedChunk
	 *            if true, then any exceptions caused by getting and reading the next chunk will be
	 *            ignored and instead make the method return null
	 * @return a new chunk loader or null if no more data is available from the chunk supplier
	 */
	private static IChunkLoader createChunkLoader(
		IChunkSupplier chunkSupplier, LoaderContext context, byte[] buffer, boolean ignoreTruncatedChunk)
			throws CouldNotLoadRecordingException, IOException {
		try {
			Chunk chunk = chunkSupplier.getNextChunk(buffer);
			if (chunk != null) {
				switch (chunk.getMajorVersion()) {
				case VERSION_0:
					return ChunkLoaderV0.create(chunk, context);
				case VERSION_1:
				case VERSION_2:
					return ChunkLoaderV1.create(chunk, context);
				default:
					throw new VersionNotSupportedException();
				}
			}
		} catch (IOException e) {
			if (ignoreTruncatedChunk) {
				LOGGER.log(Level.INFO, "Ignoring exception while reading chunk", e); //$NON-NLS-1$
			} else {
				throw e;
			}
		}
		return null;
	}
}
