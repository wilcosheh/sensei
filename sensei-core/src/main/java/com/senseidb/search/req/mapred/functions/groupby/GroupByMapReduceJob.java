package com.senseidb.search.req.mapred.functions.groupby;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.senseidb.search.req.mapred.CombinerStage;
import com.senseidb.search.req.mapred.FacetCountAccessor;
import com.senseidb.search.req.mapred.FieldAccessor;
import com.senseidb.search.req.mapred.SenseiMapReduce;

public class GroupByMapReduceJob implements SenseiMapReduce<HashMap<String, GroupedValue>, HashMap<String, GroupedValue>> {

    private static final int TRIM_SIZE = 200;
    private String[] columns;
    private String metric;
    private String function;
    private AggregateFunction aggregateFunction;
    @Override
    public void init(JSONObject params) {
      try {
        metric = params.getString("metric");
        function = params.getString("function");
        aggregateFunction = AggregateFunctionFactory.valueOf(function, metric);
        JSONArray columnsJson = params.getJSONArray("columns");
        columns = new String[columnsJson.length()];
        for (int i = 0; i < columnsJson.length(); i++) {
          columns[i] = columnsJson.getString(i);
        }
      } catch (JSONException ex) {
        throw new RuntimeException(ex);
      }
      
    }

    @Override
    public HashMap<String, GroupedValue> map(int[] docIds, int docIdCount, long[] uids, FieldAccessor accessor, FacetCountAccessor facetCountsAccessor) {
      HashMap<String, GroupedValue> map = new HashMap<String, GroupedValue>();
      for (int i =0; i < docIdCount; i++) {
        String key = getKey(columns, accessor, docIds[i]);
        GroupedValue value = map.get(key);
        
        if (value != null) {
          value.merge(aggregateFunction.produceSingleValue(accessor, docIds[i]));
        } else {
            map.put(key, aggregateFunction.produceSingleValue(accessor, docIds[i]));
        }
      }
      trimToSize(map, TRIM_SIZE * 3);
      return map;
    }

    /**Tries to trim the map to smaller size
     * @param map
     * @param count
     */
    private static void trimToSize(Map<String, ? extends Comparable> map, int count) {
        if (map.size() < count) {
            return;
        }
        double trimRatio = ((double) count) / map.size() * 2;
        if (trimRatio >= 1.0D) {
          return;
        }
        int queueSize = (int)(map.size() / Math.log(map.size()) / 4) ;
        PriorityQueue<Comparable> queue = new PriorityQueue<Comparable>(queueSize);
        int i = 0;
        int addElementRange = map.size() / queueSize;
        for (Comparable groupedValue : map.values()) {
            if (i == addElementRange) {
                i = 0;
                queue.add(groupedValue);
            } else {
                i++;
            }
        }
        int elementIndex = (int) (queue.size() * (1.0d - trimRatio));
        if (elementIndex >= queue.size()) {
          elementIndex = queue.size() - 1;
        }
       
        int counter = 0;
        Comparable newMinimumValue = null;
        while(!queue.isEmpty()) {
            if (counter == elementIndex) {
                newMinimumValue =  queue.poll();
                break;
            } else {
                counter++;
                queue.poll();
            }
        }
        if (newMinimumValue == null) {
            return;
        }
        Iterator<? extends Comparable> iterator = map.values().iterator();
        int numToRemove = map.size() - count;
       counter = 0;
       while (iterator.hasNext()) {
            if (iterator.next().compareTo( newMinimumValue) <= 0) {
                counter++;
                iterator.remove();
                 if (counter >= numToRemove) {
                 break;}
            }
        }
        
    }

    @Override
    public List<HashMap<String, GroupedValue>> combine(List<HashMap<String, GroupedValue>> mapResults, CombinerStage combinerStage) {
    
          if (mapResults.size() < 2) {
              return mapResults;
          }
          HashMap<String, GroupedValue> firstMap = mapResults.get(0);
          for (int i = 1; i < mapResults.size() ; i++) {
              merge(firstMap, mapResults.get(i));
          }
          trimToSize(firstMap,  TRIM_SIZE);
          return java.util.Arrays.asList(firstMap);
     
    }

    private void merge(HashMap<String, GroupedValue> firstMap, HashMap<String, GroupedValue> secondMap) {
        for(Map.Entry<String, GroupedValue> entry : secondMap.entrySet()) {
            GroupedValue groupedValue = firstMap.get(entry.getKey());
            if (groupedValue != null) {
                groupedValue.merge(entry.getValue());
            } else {
                firstMap.put(entry.getKey(), entry.getValue());
            }
        }
        
    }

    @Override
    public HashMap<String, GroupedValue> reduce(List<HashMap<String, GroupedValue>> combineResults) {
        if (combineResults.size() == 0) {
            return null;
        }
        if (combineResults.size() == 1) {
            return combineResults.get(0);
        }
        HashMap<String, GroupedValue> firstMap = combineResults.get(0);
        for (int i = 1; i < combineResults.size() ; i++) {
            merge(firstMap, combineResults.get(i));
        }
        trimToSize(firstMap, TRIM_SIZE);
        return firstMap;
    }

    @Override
    public JSONObject render(HashMap<String, GroupedValue> reduceResult) {
      return aggregateFunction.toJson(reduceResult);
    }
   
    private String getKey(String[] columns, FieldAccessor fieldAccessor, int docId) {
      StringBuilder key = new StringBuilder(fieldAccessor.get(columns[0], docId).toString());
      for (int i = 1; i < columns.length; i++) {
        key.append(":").append(fieldAccessor.get(columns[i], docId).toString());
      }
      return key.toString();
    }
    public static void main(String[] args) {
      HashMap<String, Integer> map = new HashMap<String, Integer>();
      for (int i = 0; i < 100000; i++) {
        map.put("" + i, i);
      }
      trimToSize(map, 1000);
      System.out.println(map);
    }
    
    
  }