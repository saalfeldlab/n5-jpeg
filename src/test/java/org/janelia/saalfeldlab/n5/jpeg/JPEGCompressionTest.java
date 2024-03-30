/**
 * Copyright (c) 2019, Stephan Saalfeld
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.janelia.saalfeldlab.n5.jpeg;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Random;

import org.janelia.saalfeldlab.n5.AbstractN5Test;
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.ShortArrayDataBlock;
import org.junit.Assert;
import org.junit.Test;

import com.google.gson.GsonBuilder;

/**
 * Lazy {@link BloscCompression} test using the abstract base class.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class JPEGCompressionTest extends AbstractN5Test {

	@Override
	protected N5Reader createN5Reader(final String location, final GsonBuilder gson) throws IOException, URISyntaxException {

		return new N5FSReader(location, gson);
	}

	@Override
	protected N5Writer createN5Writer(final String location, final GsonBuilder gson) throws IOException, URISyntaxException {

		return new N5FSWriter(location, gson);
	}

	@Override
	protected String tempN5Location() throws URISyntaxException {

		final String basePath = new File(tempN5PathName()).toURI().normalize().getPath();
		return new URI("file", null, basePath, null).toString();
	}

	private static String tempN5PathName() {

		try {
			final File tmpFile = Files.createTempDirectory("n5-test-").toFile();
			tmpFile.deleteOnExit();
			return tmpFile.getCanonicalPath();
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	static protected byte[] byteBlock;
	{
		final Random rnd = new Random();
		byteBlock = new byte[blockSize[0] * blockSize[1] * blockSize[2]];
		rnd.nextBytes(byteBlock);
	}

	static protected short[] shortBlock;
	{
		final Random rnd = new Random();
		shortBlock = new short[blockSize[0] * blockSize[1] * blockSize[2]];
		for (int i = 0; i < shortBlock.length; ++i)
			shortBlock[i] = (short)rnd.nextInt();
	}


	@Override protected N5Writer createN5Writer() throws IOException, URISyntaxException {

		return new N5FSWriter(tempN5Location(), new GsonBuilder()) {
			@Override public void close() {

				super.close();
				remove();
			}
		};
	}

	@Override
	@Test
	public void testWriteReadShortBlock() throws URISyntaxException {

		try (N5Writer n5 = createN5Writer()) {

			final int[] tolerances = new int[] {512};

			final Compression[] compressions = new Compression[] {
					new JPEGCompression(100, DataType.UINT16, 0, 65535, 1)
				};

			int i = 0;
			for (final Compression compression : compressions) {
				for (final DataType dataType : new DataType[]{
						DataType.UINT16}) {

					System.out.println("Testing " + compression.getType() + " " + dataType);

					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final ShortArrayDataBlock dataBlock = new ShortArrayDataBlock(blockSize, new long[]{0, 0, 0}, shortBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					unsignedShortArrayEqualsApproximately(shortBlock, (short[])loadedDataBlock.getData(), tolerances[i]);

					Assert.assertTrue(n5.remove(datasetName));

				}
				++i;
			}
		} catch (final IOException e) {
			Assert.fail(e.getMessage());
		}
	}

	@Override
	@Test
	public void testWriteReadIntBlock() {}

	@Override
	@Test
	public void testWriteReadLongBlock() {}

	@Override
	@Test
	public void testWriteReadFloatBlock() {}


	@Override
	@Test
	public void testWriteReadDoubleBlock() {}

	@Override
	@Test
	public void testMode1WriteReadByteBlock() {}

	@Override
	@Test
	public void testWriteReadSerializableBlock() {}

	@Override
	@Test
	public void testWriteReadByteBlock() throws URISyntaxException {

		try (N5Writer n5 = createN5Writer()) {

			final int[] tolerances = new int[] {1, 7, 10, 20};

			final Compression[] compressions = new Compression[] {
					new JPEGCompression(),
					new JPEGCompression(97),
					new JPEGCompression(95),
					new JPEGCompression(100, DataType.UINT8, 0, 255, 2)
				};

			int i = 0;
			for (final Compression compression : compressions) {
				for (final DataType dataType : new DataType[]{
						DataType.UINT8,
						DataType.INT8}) {

					System.out.println("Testing " + compression.getType() + " " + dataType);

					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(blockSize, new long[]{0, 0, 0}, byteBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					unsignedByteArrayEqualsApproximately(byteBlock, (byte[])loadedDataBlock.getData(), tolerances[i]);

					Assert.assertTrue(n5.remove(datasetName));

				}
				++i;
			}
		} catch (final IOException e) {
			Assert.fail(e.getMessage());
		}
	}

	protected void unsignedByteArrayEqualsApproximately(
			final byte[] expected,
			final byte[] actual,
			final int tolerance) {

		for (int i = 0; i < expected.length; ++i) {

			Assert.assertTrue(
					"expected:<" + (expected[i] & 0xff) + "> but was:<" + (actual[i] & 0xff) + ">",
					Math.abs((expected[i] & 0xff) - (actual[i] & 0xff)) <= tolerance);
		}
	}

	protected void unsignedShortArrayEqualsApproximately(
			final short[] expected,
			final short[] actual,
			final int tolerance) {

		for (int i = 0; i < expected.length; ++i) {

			Assert.assertTrue(
					"expected:<" + (expected[i] & 0xffff) + "> but was:<" + (actual[i] & 0xffff) + ">",
					Math.abs((expected[i] & 0xffff) - (actual[i] & 0xffff)) <= tolerance);
		}
	}
}
