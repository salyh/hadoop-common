/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapred;

import java.io.IOException;
import java.util.Arrays;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.util.ReflectionUtils;

/** An {@link OutputFormat} that writes {@link MapFile}s. */
public class MapFileOutputFormat extends OutputFormatBase {

  public RecordWriter getRecordWriter(FileSystem ignored, JobConf job,
                                      String name, Progressable progress)
    throws IOException {

    Path outputPath = job.getCurrentOutputPath();
    FileSystem fs = outputPath.getFileSystem(job);
    if (!fs.exists(outputPath)) {
      throw new IOException("Output directory doesnt exist");
    }
    Path file = new Path(outputPath, name);
    
    CompressionCodec codec = null;
    CompressionType compressionType = CompressionType.NONE;
    if (getCompressOutput(job)) {
      // find the kind of compression to do
      compressionType = SequenceFileOutputFormat.getOutputCompressionType(job);

      // find the right codec
      Class codecClass = getOutputCompressorClass(job, DefaultCodec.class);
      codec = (CompressionCodec) 
        ReflectionUtils.newInstance(codecClass, job);
    }
    
    // ignore the progress parameter, since MapFile is local
    final MapFile.Writer out =
      new MapFile.Writer(job, fs, file.toString(),
                         job.getOutputKeyClass(),
                         job.getOutputValueClass(),
                         compressionType, codec,
                         progress);

    return new RecordWriter<WritableComparable, Writable>() {

        public void write(WritableComparable key, Writable value)
          throws IOException {

          out.append(key, value);
        }

        public void close(Reporter reporter) throws IOException { out.close();}
      };
  }

  /** Open the output generated by this format. */
  public static MapFile.Reader[] getReaders(FileSystem ignored, Path dir,
                                            Configuration conf)
    throws IOException {
    FileSystem fs = dir.getFileSystem(conf);
    Path[] names = fs.listPaths(dir);

    // sort names, so that hash partitioning works
    Arrays.sort(names);
    
    MapFile.Reader[] parts = new MapFile.Reader[names.length];
    for (int i = 0; i < names.length; i++) {
      parts[i] = new MapFile.Reader(fs, names[i].toString(), conf);
    }
    return parts;
  }
    
  /** Get an entry from output generated by this class. */
  public static <K extends WritableComparable, V extends Writable>
  Writable getEntry(MapFile.Reader[] readers,
                                  Partitioner<K, V> partitioner,
                                  K key,
                                  V value) throws IOException {
    int part = partitioner.getPartition(key, value, readers.length);
    return readers[part].get(key, value);
  }

}

