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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.function.Consumer;

@Command(name = "App", version = "App 1.0-SNAPSHOT", mixinStandardHelpOptions = true)
public class App implements Runnable {

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
    private static final Logger LOGGER = LogManager.getLogger(App.class);

    @Option(names = { "-p", "--checkpoint" }, description = "checkpoint interval")
    public static int checkPointInterval = 10;

    public static final Consumer<EventContext> PARTITION_PROCESSOR = eventContext -> {
        PartitionContext partitionContext = eventContext.getPartitionContext();
        EventData eventData = eventContext.getEventData();

        LOGGER.info("Processing event from partition %s with sequence number %d with body: %s%n",
                partitionContext.getPartitionId(), eventData.getSequenceNumber(), eventData.getBodyAsString());

        // Every 10 events received, it will update the checkpoint stored in Azure Blob
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
        LOGGER.info(String.format("checkpoint interval %d!", checkPointInterval));

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

        System.out.println("Press enter to stop.");
        try {
            System.in.read();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("Stopping event processor");
        eventProcessorClient.stop();
        System.out.println("Event processor stopped.");

        System.out.println("Exiting process");
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args));
    }

}
