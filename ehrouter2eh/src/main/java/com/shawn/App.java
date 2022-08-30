package com.shawn;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.eventhubs.*;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.PartitionContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Option;

import java.util.function.Consumer;

public class App implements Runnable {
//    private final static Logger log =
    private static Logger log = LoggerFactory.getLogger(App.class);

    //TODO: filter logs as issue with package: see https://github.com/Azure/azure-sdk-for-java/issues/26071#issuecomment-1013474419
    //ALSO: https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/eventhubs/azure-messaging-eventhubs/TROUBLESHOOTING.md
    private static final Dotenv dotenv = Dotenv.configure().load();
    private static final String downstreamConnectionString = dotenv.get("EH_CONNECTION_STRING_DOWNSTREAM");
    private static final String downstreamEventHubName = dotenv.get("EH_NAME_DOWNSTREAM");

    private static final String upstreamConnectionString = dotenv.get("EH_CONNECTION_STRING_UPSTREAM");
    private static final String upstreamEventHubName = dotenv.get("EH_NAME_UPSTREAM");
    private static final String storageConnectionString = dotenv.get("ST_CONNECTION_STRING");
    private static final String storageContainerName = dotenv.get("ST_CONTAINER_NAME");

    private static final EventHubProducerClient producer = new EventHubClientBuilder()
            .connectionString(downstreamConnectionString, downstreamEventHubName)
            .buildProducerClient();

    private static int checkPointInterval = Integer.parseInt(dotenv.get("CHECKPOINT_INTERVAL", "10"));;

    public static final Consumer<EventContext> PARTITION_PROCESSOR = eventContext -> {
        PartitionContext partitionContext = eventContext.getPartitionContext();
        EventData eventData = eventContext.getEventData();

        log.info(String.format("Processing event from partition %s with sequence number %d with body: %s%n",
                partitionContext.getPartitionId(), eventData.getSequenceNumber(), eventData.getBodyAsString()));


        //TODO: move to an interlocked queue...
        EventDataBatch batch = producer.createBatch();
        batch.tryAdd(eventData);
        producer.send(batch);
        // Every N events received, it will update the checkpoint stored in Azure Blob
        // Storage.
        if (eventData.getSequenceNumber() % checkPointInterval== 0) {
            eventContext.updateCheckpoint();
        }
    };

    public static final Consumer<ErrorContext> ERROR_HANDLER = errorContext -> {
        System.out.printf("Error occurred in partition processor for partition %s, %s.%n",
                errorContext.getPartitionContext().getPartitionId(),
                errorContext.getThrowable());
    };

    @Override
    public void run() {
        log.info(String.format("checkpoint interval %d!", checkPointInterval));

        DefaultAzureCredential defaultCredential = new DefaultAzureCredentialBuilder().build();

        BlobContainerAsyncClient blobContainerAsyncClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(storageContainerName)
                .buildAsyncClient();

        // Create a builder object that you will use later to build an event processor
        // client to receive and process events and errors.
        EventProcessorClientBuilder eventProcessorClientBuilder = new EventProcessorClientBuilder()
                .connectionString(upstreamConnectionString, upstreamEventHubName)
                .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                .processEvent(PARTITION_PROCESSOR)
                .processError(ERROR_HANDLER)
                .checkpointStore(new BlobCheckpointStore(blobContainerAsyncClient));

        // Use the builder object to create an event processor client
        EventProcessorClient eventProcessorClient = eventProcessorClientBuilder.buildEventProcessorClient();

        System.out.println("Starting event processor");
        eventProcessorClient.start();


        while(eventProcessorClient.isRunning()){
            try {
                wait(1000);
            } catch (InterruptedException e) {
                eventProcessorClient.stop();
                log.warn("InterruptedException occured");
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        var app = new App();
        app.run();
    }

}
