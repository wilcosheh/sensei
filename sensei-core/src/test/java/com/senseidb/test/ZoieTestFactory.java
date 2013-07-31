package com.senseidb.test;

import java.io.File;

import proj.zoie.api.DirectoryManager.DIRECTORY_MODE;
import proj.zoie.api.indexing.ZoieIndexableInterpreter;
import proj.zoie.impl.indexing.ZoieConfig;
import proj.zoie.impl.indexing.ZoieSystem;

import com.browseengine.bobo.api.BoboIndexReader;
import com.senseidb.search.node.SenseiIndexReaderDecorator;
import com.senseidb.search.node.SenseiZoieSystemFactory;

public class ZoieTestFactory<T> extends SenseiZoieSystemFactory<T> {

  public ZoieTestFactory(File idxDir, ZoieIndexableInterpreter<T> interpreter,
      SenseiIndexReaderDecorator indexReaderDecorator, ZoieConfig zoieConfig) {
    super(idxDir, DIRECTORY_MODE.SIMPLE, interpreter, indexReaderDecorator, zoieConfig);
  }

  @Override
  public ZoieSystem<BoboIndexReader, T> getZoieInstance(int nodeId, int partitionId) {
    File partDir = getPath(nodeId, partitionId);
    if (!partDir.exists()) {
      partDir.mkdirs();
    }
    return new ZoieSystem<BoboIndexReader, T>(partDir, _interpreter, _indexReaderDecorator,
        _zoieConfig);
  }

  @Override
  public File getPath(int nodeId, int partitionId) {
    return _idxDir;
  }
}
