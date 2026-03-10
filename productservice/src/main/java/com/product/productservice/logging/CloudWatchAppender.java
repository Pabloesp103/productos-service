package main.java.com.product.productservice.logging;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Plugin(name = "CloudWatchAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class CloudWatchAppender extends AbstractAppender {

    private final CloudWatchLogsClient client;
    private final String logGroupName;
    private final String logStreamName;
    private final AtomicReference<String> sequenceToken = new AtomicReference<>();

    protected CloudWatchAppender(String name, Filter filter, Layout<?> layout,
            String logGroupName, String logStreamName,
            String endpoint) {
        super(name, filter, layout, true, null);
        this.logGroupName = logGroupName;
        this.logStreamName = logStreamName;

        this.client = CloudWatchLogsClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();

        ensureLogGroupAndStreamExist();
    }

    private void ensureLogGroupAndStreamExist() {
        try {
            try {
                client.createLogGroup(CreateLogGroupRequest.builder()
                        .logGroupName(logGroupName)
                        .build());
            } catch (ResourceAlreadyExistsException ignored) {
                // Log group ya existe
            }

            try {
                client.createLogStream(CreateLogStreamRequest.builder()
                        .logGroupName(logGroupName)
                        .logStreamName(logStreamName)
                        .build());
            } catch (ResourceAlreadyExistsException ignored) {
                // Log stream ya existe
            }
        } catch (Exception e) {
            System.err.println("Error creando log group/stream en LocalStack: " + e.getMessage());
        }
    }

    @PluginFactory
    public static CloudWatchAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute("logGroupName") String logGroupName,
            @PluginAttribute("logStreamName") String logStreamName,
            @PluginAttribute("endpoint") String endpoint,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<?> layout) {
        return new CloudWatchAppender(name, filter, layout, logGroupName, logStreamName, endpoint);
    }

    @Override
    public void append(LogEvent event) {
        String message = new String(getLayout().toByteArray(event));

        InputLogEvent logEvent = InputLogEvent.builder()
                .timestamp(Instant.now().toEpochMilli())
                .message(message)
                .build();

        try {
            PutLogEventsRequest.Builder requestBuilder = PutLogEventsRequest.builder()
                    .logGroupName(logGroupName)
                    .logStreamName(logStreamName)
                    .logEvents(List.of(logEvent));

            String token = sequenceToken.get();
            if (token != null) {
                requestBuilder.sequenceToken(token);
            }

            PutLogEventsResponse response = client.putLogEvents(requestBuilder.build());
            if (response.nextSequenceToken() != null) {
                sequenceToken.set(response.nextSequenceToken());
            }
        } catch (InvalidSequenceTokenException e) {
            sequenceToken.set(e.expectedSequenceToken());
            append(event);
        } catch (DataAlreadyAcceptedException e) {
            sequenceToken.set(e.expectedSequenceToken());
        } catch (Exception e) {
            System.err.println("Error enviando log a CloudWatch/LocalStack: " + e.getMessage());
        }
    }
}