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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.Compression.CompressionType;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DefaultBlockReader;
import org.janelia.saalfeldlab.n5.DefaultBlockWriter;

@CompressionType("jpeg")
public class JPEGCompression implements DefaultBlockReader, DefaultBlockWriter, Compression {

	@CompressionParameter
	private final int quality;

	public JPEGCompression(final int quality) {

		this.quality = quality;
	}

	public JPEGCompression() {

		this(100);
	}

	@Override
	public InputStream getInputStream(final InputStream in) throws IOException {

		final BufferedImage img = ImageIO.read(in);
		final byte[] bytes = ((DataBufferByte)img.getData().getDataBuffer()).getData();
		return new ByteArrayInputStream(bytes);

	}

	/**
	 * Not used in this implementation
	 */
	@Override
	public OutputStream getOutputStream(final OutputStream out) throws IOException {

		return null;
	}

	@Override
	public <T> void write(
			final DataBlock<T> dataBlock,
			final OutputStream out) throws IOException {

		final int[] blockSize = dataBlock.getSize();
		int height = 1;
		for (int i = 1; i < blockSize.length; ++i) {
			height *= blockSize[i];
		}
		final ByteBuffer buffer = dataBlock.toByteBuffer();
		final int width = (int)Math.ceil(buffer.capacity() / (double)height);
		final BufferedImage img = new BufferedImage(
				width,
				height,
				BufferedImage.TYPE_BYTE_GRAY);
		img.setData(
				Raster.createRaster(
						img.getSampleModel(),
						new DataBufferByte(buffer.array(), buffer.capacity()),
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
