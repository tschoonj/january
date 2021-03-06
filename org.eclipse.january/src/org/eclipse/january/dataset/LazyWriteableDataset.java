/*-
 * Copyright 2015, 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.eclipse.january.dataset;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.io.ILazyAsyncSaver;
import org.eclipse.january.io.ILazySaver;

/**
 * Subclass of lazy dataset that allows setting slices
 */
public class LazyWriteableDataset extends LazyDynamicDataset implements ILazyWriteableDataset {
	private static final long serialVersionUID = -679846418938412535L;
	private int[] chunks;
	private ILazySaver saver;
	private Object fillValue;
	private boolean writeAsync;

	/**
	 * Create a lazy dataset
	 * @param name
	 * @param dtype dataset type
	 * @param elements
	 * @param shape
	 * @param maxShape
	 * @param chunks
	 * @param saver
	 */
	public LazyWriteableDataset(String name, int dtype, int elements, int[] shape, int[] maxShape, int[] chunks, ILazySaver saver) {
		super(name, dtype, elements, shape, maxShape, saver);
		this.chunks = chunks == null ? null : chunks.clone();
		this.saver = saver;

		size = ShapeUtils.calcLongSize(this.shape);
	}

	/**
	 * Create a lazy dataset
	 * @param name
	 * @param dtype dataset type
	 * @param shape
	 * @param maxShape
	 * @param chunks
	 * @param saver
	 */
	public LazyWriteableDataset(String name, int dtype, int[] shape, int[] maxShape, int[] chunks, ILazySaver saver) {
		this(name, dtype, 1, shape, maxShape, chunks, saver);
	}

	/**
	 * Create a lazy dataset
	 * @param name
	 * @param clazz dataset element class
	 * @param elements
	 * @param shape
	 * @param maxShape
	 * @param chunks
	 * @param saver
	 */
	public LazyWriteableDataset(String name, Class<?> clazz, int elements, int[] shape, int[] maxShape, int[] chunks, ILazySaver saver) {
		this(name, DTypeUtils.getDTypeFromClass(clazz), elements, shape, maxShape, chunks, saver);
	}

	/**
	 * Create a lazy dataset
	 * @param name
	 * @param clazz dataset element class
	 * @param shape
	 * @param maxShape
	 * @param chunks
	 * @param saver
	 */
	public LazyWriteableDataset(String name, Class<?> clazz, int[] shape, int[] maxShape, int[] chunks, ILazySaver saver) {
		this(name, DTypeUtils.getDTypeFromClass(clazz), 1, shape, maxShape, chunks, saver);
	}

	/**
	 * Create a lazy writeable dataset based on in-memory data (handy for testing)
	 * @param dataset
	 */
	public static LazyWriteableDataset createLazyDataset(final Dataset dataset) {
		return createLazyDataset(dataset, null);
	}

	/**
	 * Create a lazy writeable dataset based on in-memory data (handy for testing)
	 * @param dataset
	 */
	public static LazyWriteableDataset createLazyDataset(final Dataset dataset, final int[] maxShape) {
		return new LazyWriteableDataset(dataset.getName(), dataset.getDType(), dataset.getElementsPerItem(), dataset.getShape(),
				maxShape, null,
		new ILazySaver() {
			private static final long serialVersionUID = ILazySaver.serialVersionUID;

			Dataset d = dataset;
			@Override
			public boolean isFileReadable() {
				return true;
			}

			@Override
			public boolean isFileWriteable() {
				return true;
			}

			@Override
			public void initialize() throws IOException {
			}

			@Override
			public Dataset getDataset(IMonitor mon, SliceND slice) throws IOException {
				return d.getSlice(mon, slice);
			}

			@Override
			public void setSlice(IMonitor mon, IDataset data, SliceND slice) throws IOException {
				if (slice.isExpanded()) {
					Dataset od = d;
					d = DatasetFactory.zeros(od.getClass(), slice.getSourceShape());
					d.setSlice(od, SliceND.createSlice(od, null, null));
				}
				d.setSlice(data, slice);
			}
		});
	}

	@Override
	public int[] getChunking() {
		return chunks;
	}

	@Override
	public void setChunking(int... chunks) {
		this.chunks = chunks == null ? null : chunks.clone();
	}

	@Override
	public LazyWriteableDataset clone() {
		LazyWriteableDataset ret = new LazyWriteableDataset(new String(name), getDType(), getElementsPerItem(), 
				oShape, maxShape, chunks, saver);
		ret.shape = shape;
		ret.size = size;
		ret.prepShape = prepShape;
		ret.postShape = postShape;
		ret.begSlice = begSlice;
		ret.delSlice = delSlice;
		ret.map = map;
		ret.base = base;
		ret.metadata = copyMetadata();
		ret.oMetadata = oMetadata;
		ret.eventDelegate = eventDelegate;
		return ret;
	}

	@Override
	public LazyWriteableDataset getSliceView(int[] start, int[] stop, int[] step) {
		return (LazyWriteableDataset) super.getSliceView(start, stop, step);
	}

	@Override
	public LazyWriteableDataset getSliceView(Slice... slice) {
		return (LazyWriteableDataset) super.getSliceView(slice);
	}

	@Override
	public LazyWriteableDataset getSliceView(SliceND slice) {
		return (LazyWriteableDataset) super.getSliceView(slice);
	}

	@Override
	public LazyWriteableDataset getTransposedView(int... axes) {
		return (LazyWriteableDataset) super.getTransposedView(axes);
	}

	@Override
	public void setWritingAsync(boolean async) {
		writeAsync = async;
	}

	/**
	 * Set a slice of the dataset
	 * 
	 * @param data
	 * @param slice an n-D slice
	 * @throws DatasetException 
	 */
	public void setSlice(IDataset data, SliceND slice) throws DatasetException {
		setSlice(null, data, slice);
	}

	@Override
	public void setSlice(IMonitor monitor, IDataset data, int[] start, int[] stop, int[] step) throws DatasetException {
		internalSetSlice(monitor, writeAsync, data, new SliceND(shape, maxShape, start, stop, step));
	}

	@Override
	public void setSlice(IMonitor monitor, IDataset data, SliceND slice) throws DatasetException {
		internalSetSlice(monitor, writeAsync, data, slice);
	}

	@Override
	public void setSliceSync(IMonitor monitor, IDataset data, SliceND slice) throws DatasetException {
		internalSetSlice(monitor, false, data, slice);
	}

	private void internalSetSlice(IMonitor monitor, final boolean async, IDataset data, SliceND slice) throws DatasetException {
		int[] dshape = data instanceof Dataset ? ((Dataset) data).getShapeRef() : data.getShape();
		if (dshape.length == 0) { // fix zero-rank case
			dshape = new int[] {1}; // FIXME remove
		}
		// if necessary, reshape the input data according to the shape of the slice
		if (!Arrays.equals(slice.getShape(), dshape)) {
			data = data.getSliceView();
			data.setShape(slice.getShape());
		}

		SliceND nslice = calcTrueSlice(slice);
		data = transformInput(data);

		if (base != null) {
			((ILazyWriteableDataset) base).setSlice(monitor, data, nslice);
		} else {
			if (saver == null) {
				throw new DatasetException("Cannot write to file as saver not defined!");
			}

			try {
				if (async && saver instanceof ILazyAsyncSaver) {
					((ILazyAsyncSaver)saver).setSliceAsync(monitor, data, nslice);
				} else {
					if (!saver.isFileWriteable()) {
						throw new DatasetException("Cannot write to file as it is not writeable!");
					}
					saver.setSlice(monitor, data, nslice);
				}
			} catch (IOException e) {
				throw new DatasetException("Could not save dataset", e);
			}
			if (!refreshShape()) { // send event as data has changed
				eventDelegate.fire(new DataEvent(name, shape));
			}
		}
	}

	/**
	 * Set saver (and also loader)
	 * @param saver
	 */
	@Override
	public void setSaver(ILazySaver saver) {
		this.saver = saver;
		this.loader = saver;
	}

	@Override
	protected SliceND createSlice(int[] nstart, int[] nstop, int[] nstep) {
		if (base == null) {
			return new SliceND(oShape, maxShape, nstart, nstop, nstep);
		}
		return base.createSlice(nstart, nstop, nstep);
	}

	@Override
	public Object getFillValue() {
		return fillValue;
	}

	@Override
	public void setFillValue(Object fill) {
		fillValue = fill;
	}
}
