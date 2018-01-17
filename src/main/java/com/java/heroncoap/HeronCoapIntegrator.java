package com.java.heroncoap;

import backtype.storm.metric.api.GlobalMetrics;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.topology.base.BaseWindowedBolt.Count;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;
import org.bson.Document;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import com.twitter.heron.api.bolt.BaseWindowedBolt;
import com.twitter.heron.api.windowing.TupleWindow;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.EndpointManager;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.java.heroncoap.SlidingWindowSumBolt;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoServerException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.twitter.heron.api.HeronSubmitter;
import com.twitter.heron.common.basics.ByteAmount;

/**
 * This is a Sample topology for storm where we consume a stream of words and executes an append operation to them.
 */
public final class HeronCoapIntegrator{

    private HeronCoapIntegrator() {}

    public static void main(String[] args) throws Exception {

        // Instantiate a topology builder to build the tag
        TopologyBuilder builder = new TopologyBuilder();


        // Define the parallelism hint for the topololgy
        final int parallelism = 1;

        // Build the topology to have a 'word' spout and 'exclaim' bolt
        // also, set the 'word' spout bolt to have two instances
        builder.setSpout("word", new TestWordSpout(), parallelism);

        // Specify that 'exclaim1' bolt should consume from 'word' spout using
        // Shuffle grouping running in four instances
        builder.setBolt("coap_tuples", new ExclamationBolt(), 1 * parallelism)
                .shuffleGrouping("word");
        
        builder.setBolt("slidingsum", new SlidingWindowSumBolt().withTumblingWindow(Count.of(10)), 1*parallelism)
        .fieldsGrouping("coap_tuples", new Fields("sensorid"));
        
        builder.setBolt("mongo_persist", new MongoPersistBolt(), 1 * parallelism).shuffleGrouping("slidingsum");
        
        // Create the config for the topology
        Config conf = new Config();

        // Set the run mode to be debug
        conf.setDebug(true);
        
       // conf.setSkipMissingKryoRegistrations(false);



        // Set the number of tuples to be in flight at any given time to be 10
        conf.setMaxSpoutPending(1000000);
        //conf.setMessageTimeoutSecs(6000000);

        // Set JVM options to dump the heap when out of memory
        conf.put(Config.TOPOLOGY_WORKER_CHILDOPTS, "-XX:+HeapDumpOnOutOfMemoryError");
        //conf.put(Config.TRANSACTIONAL_ZOOKEEPER_SERVERS, new CoapServer(8888));

        // If the topology name is specified run in the cluster mode, otherwise run in
        // Simulator mode
        if (args != null && args.length > 0) {

            // Set the number of containers to be two
            conf.setNumWorkers(parallelism);

            // Submit the topology to be run in a cluster
            StormSubmitter.submitTopology(args[0], conf, builder.createTopology());
        } else {
            System.out.println("Topology name not provided as an argument, running in simulator mode.");

            // Create the local cluster for simulation
            LocalCluster cluster = new LocalCluster();

            // Submit the topology to the simulated cluster
            cluster.submitTopology("test", conf, builder.createTopology());

            // Wait for it run 10 secs
            Utils.sleep(10000);

            // Kill and shutdown the topology after the elapsed time
            cluster.killTopology("test");
            cluster.shutdown();
        }
    }


    /**
     * Word Spout that outputs a ranom word among a list of words continuously
     */
    static class TestWordSpout extends BaseRichSpout{

        private static final long serialVersionUID = -3217886193225455451L;
        private SpoutOutputCollector collector;
        private String URI;
        private CoapServer server;
        private String content;
        private String sensorId;
        private long count = 0;
     

        // Intantiate with no throttle duration
        public TestWordSpout()
        {
        	
        }
        
            
        // Intantiate with specified throtte duration
        
        
        @SuppressWarnings("rawtypes")
        @Override
        public void open(
                Map conf,
                TopologyContext context,
                SpoutOutputCollector collector) {
        	
        	server = new CoapServer(3333);
        	for (InetAddress addr : EndpointManager.getEndpointManager().getNetworkInterfaces()) {
    			if (addr instanceof Inet4Address) {
    				InetSocketAddress bindToAddress = new InetSocketAddress(addr, 5683);
    				server.addEndpoint(new CoapEndpoint(bindToAddress));
    			}
    		}
        	//server.addEndpoint(new CoapEndpoint(2222));
        	server.add(new CoapResource("helloWorld")  {
       		 @Override
       	 	   public void handlePUT(CoapExchange exchange) {
       			ByteArrayInputStream bais = new ByteArrayInputStream(exchange.getRequestPayload());
       	 		List<DataItem> dataItems;
       	 		try 
       	 		{ 
		       	 		dataItems = new CborDecoder(bais).decode(); 
		   	 			for(DataItem dataItem : dataItems) 
		   	 			{
		   	 				String value = dataItem.toString();
		       	 		value = value.substring(1, value.length()-1);           //remove curly brackets
		       	 		String[] keyValuePairs = value.split(",");              //split the string to creat key-value pairs
		       	 		Map<String,String> map = new HashMap<>();               
		
		       	 		for(String pair : keyValuePairs)                        //iterate over the pairs
		       	 		{
		       	 		    String[] entry = pair.split(":");                   //split the pairs to get key and value 
		       	 		    map.put(entry[0].trim(), entry[1].trim());          //add them to the hashmap and trim whitespaces
		       	 		}
		       	 		content = map.get("value");
		       	 		sensorId = map.get("subject");
		   	 			}
		   	        	count = count+1;
		   		 		System.out.println(count);
		   	 			
       	 		}  
       	 		catch (CborException e) 
       	 		{
       	 			System.out.println(e.getMessage()); 
       	 		}
       	 		 
       	 		 }
   	    });
        	server.start();
 
        	this.collector = collector;	
        				
        }
        
       
        
        // This method is called to generate the next sequence for the spout
        @Override
        public void nextTuple() {
        	
        if(content!=null && sensorId!=null)
        {
        	//System.out.println("emitting temperature value "+content);
        	collector.emit(new Values(content,sensorId));
        	content=null;
        	sensorId = null;
        }
         
        }
        
        @Override
        public void close()
        {
        	server.stop();
        }


        // This method speisifes the output field labels
        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("word","sensorid")); // Here we tagethe output tuple with the tag "word"
        }

	

    }
    


    /**
     * ExclamationBolt Bolt that takes a string word as outputs the same word with exclamation marks appended
     */
    
    
    static class ExclamationBolt extends BaseRichBolt {

        private static final long serialVersionUID = 1184860508880121352L;
        private long startTime;
        private OutputCollector collector;
        

        @Override
        @SuppressWarnings("rawtypes")
        public void prepare(Map conf,
                            TopologyContext context,
                            OutputCollector collector) {
            this.collector = collector;
            this.startTime = System.currentTimeMillis();
            
        }

        @Override
        public void execute(Tuple tuple) {
           
                long latency = System.currentTimeMillis() - startTime;
                // Generate the appended word , emit it as output and log it in the console
                
                
                String appendedWord = null;
                String sensorId = null;
                appendedWord = tuple.getString(0) ;
                sensorId = tuple.getString(1);
                double temp;
                if(appendedWord!=null && sensorId!=null)
                {
                	temp = Double.parseDouble(appendedWord);
                	this.collector.emit(new Values(sensorId,temp));
                	this.collector.ack(tuple);
                    System.out.println(temp);
                    
               }
 
            
        }

        // This method specifies the output field labels
        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
            declarer.declare(new Fields("sensorId","tuples")); // Here we tagethe output tuple with the tag "modified_word"
        }
    }
    
   static class MongoPersistBolt  extends BaseRichBolt{
    	
    	private static final long serialVersionUID = 1184860508880121352L;
        private OutputCollector collector;
        private MongoClient mongoClient;
        private MongoDatabase database;
        private MongoCollection<Document>  collection;
    	@Override
    	public void execute(Tuple tuple) {
    		String sensorId = tuple.getString(0);
    		Double temp_avg = tuple.getDouble(1);
    		Date now = new Date();

    		Document document = new Document("time_inserted",  new Date())
    								.append("sensor_id", sensorId)
    								.append("event_type", "temperature")
    								.append("value", temp_avg);

    		
    		try {
    			collection.insertOne(document);
    		}
    		catch(MongoServerException e) {
    			e.printStackTrace();
    		}
       		
    	}

    	@Override
    	public void prepare(Map arg0, TopologyContext arg1, OutputCollector collector) {

    		this.collector = collector;
   
    		//for quartz, if running else where the local host should be changed to quartz ip
    		mongoClient = new MongoClient( "localhost" , 27017 );	
    		database = mongoClient.getDatabase("heron_stream");
    		collection = database.getCollection("sensor_moving_avg");
    	}

    	@Override
    	public void declareOutputFields(OutputFieldsDeclarer declarer) {
    		declarer.declare(new Fields("date","sensorId","temperature", "temp_avg", "doc"));
    		
    	}

    	@Override
    	public void cleanup() {
    		mongoClient.close();
    		
    	}

    	

    }
   
   
 
}