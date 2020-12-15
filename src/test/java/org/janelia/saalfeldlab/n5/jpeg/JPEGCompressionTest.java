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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Random;

import org.janelia.saalfeldlab.n5.AbstractN5Test;
import org.janelia.saalfeldlab.n5.ByteArrayDataBlock;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.junit.Assert;
import org.junit.Test;

/**
 * Lazy {@link BloscCompression} test using the abstract base class.
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class JPEGCompressionTest extends AbstractN5Test {

	static private String testDirPath = System.getProperty("user.home") + "/tmp/n5-test";

	static protected byte[] byteBlock;
	{
		final Random rnd = new Random();
		byteBlock = new byte[blockSize[0] * blockSize[1] * blockSize[2]];
		rnd.nextBytes(byteBlock);
	}


	/**
	 * @throws IOException
	 */
	@Override
	protected N5Writer createN5Writer() throws IOException {

		return new N5FSWriter(testDirPath);
	}

	@Override
	protected Compression[] getCompressions() {

		return new Compression[] {
				new JPEGCompression(),
				new JPEGCompression(97),
				new JPEGCompression(95)
			};
	}

	@Override
	@Test
	public void testWriteReadShortBlock() {}

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
	public void testWriteReadByteBlock() {

		final int[] tolerances = new int[] {1, 5, 10};

		int i = 0;
		for (final Compression compression : getCompressions()) {
			for (final DataType dataType : new DataType[]{
					DataType.UINT8,
					DataType.INT8}) {

				System.out.println("Testing " + compression.getType() + " " + dataType);
				try {
					n5.createDataset(datasetName, dimensions, blockSize, dataType, compression);
					final DatasetAttributes attributes = n5.getDatasetAttributes(datasetName);
					final ByteArrayDataBlock dataBlock = new ByteArrayDataBlock(blockSize, new long[]{0, 0, 0}, byteBlock);
					n5.writeBlock(datasetName, attributes, dataBlock);

					final DataBlock<?> loadedDataBlock = n5.readBlock(datasetName, attributes, new long[]{0, 0, 0});

					unsignedByteArrayEqualsApproximately(byteBlock, (byte[])loadedDataBlock.getData(), tolerances[i]);

					Assert.assertTrue(n5.remove(datasetName));

				} catch (final IOException e) {
					e.printStackTrace();
					fail("Block cannot be written.");
				}
			}
			++i;
		}
	}

	protected void unsignedByteArrayEqualsApproximately(
			final byte[] expected,
			final byte[] actual,
			final int tolerance) {

		for (int i = 0; i < expected.length; ++i) {

			Assert.assertTrue(
					"expected:<" + expected[i] + "> but was:<" + actual[i] + ">",
					Math.abs((expected[i] & 0xff) - (actual[i] & 0xff)) <= tolerance);
		}
	}
}
