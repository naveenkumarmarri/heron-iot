package com.java.heroncoap;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseWindowedBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.windowing.TupleWindow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Computes sliding window sum
 */
public class SlidingWindowSumBolt extends BaseWindowedBolt {
    
    private static final long serialVersionUID = 1184860508880121352L;
    private OutputCollector collector;
    
    @Override
    public void prepare(Map<String, Object> topoConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(TupleWindow inputWindow) {
        HashMap<String, Double> windowMap = new HashMap<String,Double>();
        HashMap<String, Integer> countMap = new HashMap<String,Integer>();
        double sum = 0;
        double temperature = 0;
        String sensorId = null;
        int count = 0;
        List<Tuple> tuplesInWindow = inputWindow.get();
        
        if (tuplesInWindow.size() > 0) {
            /*
            * Since this is a tumbling window calculation,
            * we use all the tuples in the window to compute the avg.
            */
            

            for (Tuple tuple : tuplesInWindow) {
                sensorId = (String) tuple.getValue(0);
                temperature =  (double) tuple.getValue(1);
                if(windowMap.containsKey(sensorId))
                {
                    windowMap.put(sensorId, windowMap.get(sensorId)+temperature);
                    countMap.put(sensorId,countMap.get(sensorId)+1);
                    collector.emit(new Values(sensorId,temperature));
                }
                else
                {
                    windowMap.put(sensorId,temperature);
                    countMap.put(sensorId, 1);
                }
                    
            }
            for(Map.Entry<String, Double> it : windowMap.entrySet())
            {
                sensorId = it.getKey();
                sum = it.getValue();
                count = countMap.get(sensorId);
                System.out.println("The tumble window avg for device "+sensorId+" is "+sum/count);
                collector.emit(new Values(sensorId,sum/count));
            }
            
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("sensorid","avg"));
    }
}