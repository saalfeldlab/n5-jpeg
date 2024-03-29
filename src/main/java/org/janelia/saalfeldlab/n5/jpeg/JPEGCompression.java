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

	@CompressionParameter
	private final double min;

	@CompressionParameter
	private final double max;
	private final double range;

	@CompressionParameter
	private final double gamma;
	private final double invGamma;


	public JPEGCompression(
			final int quality,
			final DataType dataType,
			final double min,
			final double max,
			final double gamma) {

		this.quality = quality;
		this.dataType = dataType;
		this.min = min;
		this.max = max;
		range = max - min;
		this.gamma = gamma;
		invGamma = 1.0 / gamma;
	}

	public JPEGCompression(final int quality) {

		this(quality, DataType.UINT8, 0, 255, 1);
	}

	public JPEGCompression() {

		this(100);
	}

	private final byte map(final double value) {

		return (byte)Math.min(255, Math.max(0, Math.round(Math.pow((value - min) / range, gamma) * 255)));
	}

	private final double mapInv(final byte value) {

		return Math.pow((value & 0xff) / 255.0, invGamma) * range + min;
	}

	private final static long clip(final double value, final double min, final double max) {

		return Math.round(Math.min(max, Math.max(min, value)));
	}


	@Override
	public <T, B extends DataBlock<T>> void read(
			final B dataBlock,
			final InputStream in) throws IOException {

		final BufferedImage img = ImageIO.read(in);
		final byte[] bytes = ((DataBufferByte)img.getData().getDataBuffer()).getData();

//		new ImagePlus("", new ByteProcessor(dataBlock.getSize()[0], bytes.length / dataBlock.getSize()[0], bytes)).show();

		final Object dataBlockData = dataBlock.getData();

		if (dataType == DataType.UINT8 && min == 0 && max == 255 && gamma == 1 && dataBlockData instanceof byte[]) {
			final byte[] data = (byte[])dataBlockData;
			System.arraycopy(bytes, 0, data, 0, data.length);
			return;
		}

		switch (dataType) {
		case UINT8:
		case INT8:
			final byte[] byteData = (byte[])dataBlockData;
			if (dataType == DataType.UINT8)
				for (int i = 0; i < byteData.length; ++i)
					byteData[i] = (byte)clip(mapInv(bytes[i]), 0, 0xff);
			else
				for (int i = 0; i < byteData.length; ++i)
					byteData[i] = (byte)clip(mapInv(bytes[i]), Byte.MIN_VALUE, Byte.MAX_VALUE);
			break;
		case UINT16:
		case INT16:
			final short[] shortData = (short[])dataBlockData;
			if (dataType == DataType.UINT16)
				for (int i = 0; i < shortData.length; ++i)
					shortData[i] = (short)clip(mapInv(bytes[i]), 0, 0xffff);
			else
				for (int i = 0; i < shortData.length; ++i)
					shortData[i] = (short)clip(mapInv(bytes[i]), Short.MIN_VALUE, Short.MAX_VALUE);
			break;
		case UINT32:
		case INT32:
			final int[] intData = (int[])dataBlockData;
			if (dataType == DataType.UINT32)
				for (int i = 0; i < intData.length; ++i)
					intData[i] = (int)clip(mapInv(bytes[i]), 0, 0xffffffffL);
			else
				for (int i = 0; i < intData.length; ++i)
					intData[i] = (short)clip(mapInv(bytes[i]), Integer.MIN_VALUE, Integer.MAX_VALUE);
			break;
		case UINT64:
		case INT64:
			final long[] longData = (long[])dataBlockData;
			if (dataType == DataType.UINT64)
				for (int i = 0; i < longData.length; ++i)
					longData[i] = (long)clip(mapInv(bytes[i]), 0, 1.8446744073709552E19);
			else
				for (int i = 0; i < longData.length; ++i)
					longData[i] = (short)clip(mapInv(bytes[i]), Long.MIN_VALUE, Long.MAX_VALUE);
			break;
		case FLOAT32:
			final float[] floatData = (float[])dataBlockData;
			for (int i = 0; i < floatData.length; ++i)
				floatData[i] = (float)mapInv(bytes[i]);
			break;
		case FLOAT64:
			final double[] doubleData = (double[])dataBlockData;
			for (int i = 0; i < doubleData.length; ++i)
				doubleData[i] = mapInv(bytes[i]);
			break;
		default:
			/* default reader applies no min, max, gamma mapping and just reads the decompressed bytes */
			final ByteBuffer buffer = dataBlock.toByteBuffer();
			buffer.put(bytes, 0, buffer.capacity());
			dataBlock.readData(buffer);
		}
	}


	@Override
	public <T> void write(
			final DataBlock<T> dataBlock,
			final OutputStream out) throws IOException {

		final int[] blockSize = dataBlock.getSize();
		final int width = blockSize[0];

		byte[] bytes = new byte[dataBlock.getNumElements()];

		switch (dataType) {
		case UINT8:
		case INT8:
			final byte[] byteData = (byte[])dataBlock.getData();
			if (dataType == DataType.UINT8)
				for (int i = 0; i < bytes.length; ++i)
					bytes[i] = map(byteData[i] & 0xff);
			else
				for (int i = 0; i < bytes.length; ++i)
					bytes[i] = map(byteData[i]);
		break;
		case UINT16:
		case INT16:
			final short[] shortData = (short[])dataBlock.getData();
			if (dataType == DataType.UINT16)
				for (int i = 0; i < bytes.length; ++i)
					bytes[i] = map(shortData[i] & 0xffff);
			else
				for (int i = 0; i < bytes.length; ++i)
					bytes[i] = map(shortData[i]);
		break;
		case UINT32:
		case INT32:
			final int[] intData = (int[])dataBlock.getData();
			if (dataType == DataType.UINT32)
				for (int i = 0; i < bytes.length; ++i)
					bytes[i] = map(intData[i] & 0xffffffffL);
			else
				for (int i = 0; i < bytes.length; ++i)
					bytes[i] = map(intData[i]);
		break;
		case UINT64:
		case INT64:
			final long[] longData = (long[])dataBlock.getData();
			for (int i = 0; i < bytes.length; ++i) {
				bytes[i] = map(longData[i]);
			}
		break;
		case FLOAT32:
			final float[] floatData = (float[])dataBlock.getData();
			for (int i = 0; i < bytes.length; ++i) {
				bytes[i] = map(floatData[i]);
			}
		break;
		case FLOAT64:
			final double[] doubleData = (double[])dataBlock.getData();
			for (int i = 0; i < bytes.length; ++i) {
				bytes[i] = map(doubleData[i]);
			}
		break;
		default:
			bytes = dataBlock.toByteBuffer().array();
		}

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
