/**
 * Copyright (c) 2017--2020, Stephan Saalfeld
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

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.janelia.saalfeldlab.n5.BlockReader;
import org.janelia.saalfeldlab.n5.BlockWriter;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.Compression.CompressionType;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;

@CompressionType("jpeg")
public class JPEGCompression implements BlockReader, BlockWriter, Compression {

	@CompressionParameter
	private final int quality;

	@CompressionParameter
	private final DataType dataType;

	public JPEGCompression(final int quality, final DataType dataType) {

		this.quality = quality;
		this.dataType = dataType;
	}

	public JPEGCompression(final int quality) {

		this(quality, DataType.UINT8);
	}

	public JPEGCompression() {

		this(100);
	}

	@Override
	public <T, B extends DataBlock<T>> void read(
			final B dataBlock,
			final InputStream in) throws IOException {

		final BufferedImage img = ImageIO.read(in);
		final byte[] bytes = ((DataBufferByte)img.getData().getDataBuffer()).getData();

//		new ImagePlus("", new ByteProcessor(dataBlock.getSize()[0], bytes.length / dataBlock.getSize()[0], bytes)).show();

		if (dataType == DataType.UINT8) {
			final Object dataBlockData = dataBlock.getData();
			if (dataBlockData instanceof byte[]) {
				final byte[] data = (byte[])dataBlockData;
				if (dataType == DataType.UINT8)
					System.arraycopy(bytes, 0, data, 0, data.length);
				return;
			}
		} else if (dataType == DataType.INT8) {
			final byte[] data = (byte[])dataBlock.getData();
			for (int i = 0; i < data.length; ++i)
				data[i] = (byte)((bytes[i] & 0xff) - 127);
			return;
		}

		final int[] blockSize = dataBlock.getSize();
		final int width = blockSize[0];
		final int height = (int)Math.ceil(dataBlock.getNumElements() / width / 8);
		final int stride = width * height * 8;

		switch (dataType) {
		case UINT16:
		case INT16:
			final short[] shortData = (short[])dataBlock.getData();
			interleave(bytes, shortData, stride);
			if (dataType == DataType.INT16)
				for (int i = 0; i < shortData.length; ++i)
					shortData[i] = (short)((shortData[i] & 0xffff) - 32767);
			break;
		case UINT32:
		case INT32:
			final int[] intData = (int[])dataBlock.getData();
			interleave(bytes, intData, stride);
			if (dataType == DataType.INT32)
				for (int i = 0; i < intData.length; ++i)
					intData[i] = (int)((intData[i] & 0xffffffffL) - 1073741823);
			break;
		case UINT64:
			final long[] longData = (long[])dataBlock.getData();
			interleave(bytes, longData, stride);
			break;
		default:
			/* default reader that works for all types */
			final ByteBuffer buffer = dataBlock.toByteBuffer();
			buffer.put(bytes, 0, buffer.capacity());
			dataBlock.readData(buffer);
		}
	}

	private static void interleave(
			final byte[] in,
			final short[] out,
			final int stride) {

		for (int i = 0; i < out.length; ++i)
			out[i] = (short)(
					((in[i] & 0xff) << 8) |
					(in[i + stride] & 0xff));
	}

	private static void interleave(
			final byte[] in,
			final int[] out,
			final int stride) {

		final int stride1 = stride + stride;
		final int stride2 = stride1 + stride;

		for (int i = 0; i < out.length; ++i)
			out[i] =
					((in[i] & 0xff) << 24) |
					((in[i + stride] & 0xff) << 16) |
					((in[i + stride1] & 0xff) << 8) |
					(in[i + stride2] & 0xff);
	}

	private static void interleave(
			final byte[] in,
			final long[] out,
			final int stride) {

		final int stride1 = stride + stride;
		final int stride2 = stride1 + stride;
		final int stride3 = stride2 + stride;
		final int stride4 = stride3 + stride;
		final int stride5 = stride4 + stride;
		final int stride6 = stride5 + stride;

		for (int i = 0; i < out.length; ++i)
			out[i] =
					((in[i] & 0xffL) << 56) |
					((in[i + stride] & 0xffL) << 48) |
					((in[i + stride1] & 0xffL) << 40) |
					((in[i + stride2] & 0xffL) << 32) |
					((in[i + stride3] & 0xffL) << 24) |
					((in[i + stride4] & 0xffL) << 16) |
					((in[i + stride5] & 0xffL) << 8) |
					(in[i + stride6] & 0xffL);
	}

	private static byte[] deInterleave(final short[] data, final int stride) {

		final byte[] bytes = new byte[stride << 1];
		for (int i = 0; i < data.length; ++i) {
			bytes[i] = (byte)((data[i] >> 8) & 0xff);
			bytes[i + stride] = (byte)(data[i] & 0xff);

		}
		return bytes;
	}

	private static byte[] deInterleave(final int[] data, final int stride) {

		final int stride1 = stride + stride;
		final int stride2 = stride1 + stride;

		final byte[] bytes = new byte[stride << 2];
		for (int i = 0; i < data.length; ++i) {
			bytes[i] = (byte)((data[i] >> 24) & 0xff);
			bytes[i + stride] = (byte)((data[i] >> 16) & 0xff);
			bytes[i + stride1] = (byte)((data[i] >> 8) & 0xff);
			bytes[i + stride2] = (byte)(data[i] & 0xff);
		}
		return bytes;
	}

	private static byte[] deInterleave(final long[] data, final int stride) {

		final int stride1 = stride + stride;
		final int stride2 = stride1 + stride;
		final int stride3 = stride2 + stride;
		final int stride4 = stride3 + stride;
		final int stride5 = stride4 + stride;
		final int stride6 = stride5 + stride;

		final byte[] bytes = new byte[stride << 3];
		for (int i = 0; i < data.length; ++i) {
			bytes[i] = (byte)((data[i] >> 56) & 0xff);
			bytes[i + stride] = (byte)((data[i] >> 48) & 0xff);
			bytes[i + stride1] = (byte)((data[i] >> 40) & 0xff);
			bytes[i + stride2] = (byte)((data[i] >> 32) & 0xff);
			bytes[i + stride3] = (byte)((data[i] >> 24) & 0xff);
			bytes[i + stride4] = (byte)((data[i] >> 16) & 0xff);
			bytes[i + stride5] = (byte)((data[i] >> 8) & 0xff);
			bytes[i + stride6] = (byte)(data[i] & 0xff);
		}
		return bytes;
	}

	private <T> byte[] shuffleBytes(
			final DataBlock<T> dataBlock,
			final int stride) {

		if (dataType == DataType.UINT8) {
			final Object dataBlockData = dataBlock.getData();
			if (dataBlockData instanceof byte[])
				return (byte[])dataBlockData;
		}

		switch (dataType) {
		case UINT16:
			return deInterleave((short[])dataBlock.getData(), stride);
		case UINT32:
			return deInterleave((int[])dataBlock.getData(), stride);
		case UINT64:
			return deInterleave((long[])dataBlock.getData(), stride);
		case INT8:
			final byte[] bytes = (byte[])dataBlock.getData();
			for (int i = 0; i < bytes.length; ++i)
				bytes[i] = (byte)(bytes[i] + 127);
			return bytes;
		case INT16:
			final short[] shorts = (short[])dataBlock.getData();
			for (int i = 0; i < shorts.length; ++i)
				shorts[i] = (short)(shorts[i] + 32767);
			return deInterleave(shorts, stride);
		case INT32:
			final int[] ints = (int[])dataBlock.getData();
			for (int i = 0; i < ints.length; ++i)
				ints[i] = ints[i] + 1073741823;
			return deInterleave(ints, stride);
		default:
			return dataBlock.toByteBuffer().array();
		}
	}

	@Override
	public <T> void write(
			final DataBlock<T> dataBlock,
			final OutputStream out) throws IOException {

		final int[] blockSize = dataBlock.getSize();
		final int width = blockSize[0];
		final int height = (int)Math.ceil(dataBlock.getNumElements() / width / 8);
		final int stride = width * height * 8;

		final byte[] bytes = shuffleBytes(dataBlock, stride);
		final BufferedImage img = new BufferedImage(
				width,
				(int)Math.ceil(bytes.length / width),
				BufferedImage.TYPE_BYTE_GRAY);
		img.setData(
				Raster.createRaster(
						img.getSampleModel(),
						new DataBufferByte(bytes, bytes.length),
						new Point()));

		final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		final ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality * 0.01f);

		writer.setOutput(ImageIO.createImageOutputStream(out));
		writer.write(null, new IIOImage(img, null,  null), param);
		writer.dispose();

		out.flush();
	}

	@Override
	public JPEGCompression getReader() {

		return this;
	}

	@Override
	public JPEGCompression getWriter() {

		return this;
	}
}
