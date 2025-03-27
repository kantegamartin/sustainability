/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package no.kantega.obrc;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.IntStream;

public class Solution {
    private static final String FILE = "./measurements.txt";

    public static void main(String[] args) throws Exception
    {
        var file = new File(args.length > 0 ? args[0] : FILE);
        var fileSize = file.length();
        var numberOfProcessors = Runtime.getRuntime().availableProcessors();
        var segmentSize = (int) Math.min(Integer.MAX_VALUE, fileSize / numberOfProcessors); // bytebuffer position is an int, so can be max Integer.MAX_VALUE
        var segmentCount = (int) (fileSize / segmentSize);
        var results = IntStream.range(0, segmentCount)
                .mapToObj(segmentNr -> parseSegment(file, fileSize, segmentSize, segmentNr))
                .reduce(StationList::merge)
                .orElseGet(StationList::new)
                .toStringArray();
        Arrays.sort(results);
        System.out.println(String.join("\n", results));
    }

    private static StationList parseSegment(File file, long fileSize, int segmentSize, int segmentNr) {
        long segmentStart = segmentNr * (long) segmentSize;
        long segmentEnd = Math.min(fileSize, segmentStart + segmentSize + 100);
        try (var fileChannel = (FileChannel) Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
            var bb = fileChannel.map(FileChannel.MapMode.READ_ONLY, segmentStart, segmentEnd - segmentStart);
            if (segmentStart > 0) {
                // noinspection StatementWithEmptyBody
                while (bb.get() != '\n')
                    ; // skip to first new line
            }
            StationList stationList = new StationList();
            var buffer = new byte[100];
            while (bb.position() < segmentSize) {
                byte b;
                var i = 0;
                while ((b = bb.get()) != ';') {
                    buffer[i++] = b;
                }

                var value = new byte[5];
                var j = 0;
                while ((b = bb.get()) != '\n') {
                    value[j++] = b;
                }

                String valueString = new String(value, 0, j, StandardCharsets.UTF_8);
                stationList.add(new String(buffer, 0, i, StandardCharsets.UTF_8), Double.parseDouble(valueString));
            }

            return stationList;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Station {
        private final String name;

        private double min;
        private double max;
        private double total;
        private int count;

        public Station(String name, double value) {
            this.name = name;
            min = max = total = value;
            count = 1;
        }

        @Override
        public String toString() {
            return name + "=" + min + "/" + Math.round(10.0 * total / count) / 10.0 + "/" + max;
        }

        private void append(double min, double max, double total, int count) {
            if (min < this.min)
                this.min = min;
            if (max > this.max)
                this.max = max;
            this.total += total;
            this.count += count;
        }

        public void merge(Station other) {
            append(other.min, other.max, other.total, other.count);
        }
    }

    private static class StationList implements Iterable<Station> {
        private final Map<String, Station> array = new HashMap<>();

        public boolean add(Station station) {
            var existing = array.get(station.name);
            if (existing == null) {
                array.put(station.name, station);
                return false;
            } else {
                existing.merge(station);
                return true;
            }
        }

        public boolean add(String name, double value) {
            return add(new Station(name, value));
        }

        public String[] toStringArray() {
            var destination = new String[array.size()];

            var i = 0;
            for (Station station : this)
                destination[i++] = station.toString();

            return destination;
        }

        public StationList merge(StationList other) {
            for (Station station : other)
                add(station);
            return this;
        }

        @Override
        public Iterator<Station> iterator() {
            return array.values().iterator();
        }
    }
}
