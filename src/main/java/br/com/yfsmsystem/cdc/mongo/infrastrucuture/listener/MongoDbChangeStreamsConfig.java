package br.com.yfsmsystem.cdc.mongo.infrastrucuture.listener;

import br.com.yfsmsystem.cdc.mongo.domain.dto.Customer;
import br.com.yfsmsystem.cdc.mongo.domain.service.SendToQueueService;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.in;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class MongoDbChangeStreamsConfig implements ApplicationListener<ApplicationReadyEvent> {

    private final SendToQueueService sendToQueueService;
    private String nextSequenceId;

    public MongoDbChangeStreamsConfig(SendToQueueService sendToQueueService) {
        this.sendToQueueService = sendToQueueService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        String mongoUri = "mongodb://root:root123@localhost:27017/customer?authSource=admin";
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .build();
        try (MongoClient mongoClient = MongoClients.create(clientSettings)) {
            MongoDatabase db = mongoClient.getDatabase(Objects.requireNonNull(connectionString.getDatabase()));
            MongoCollection<Customer> customers = db.getCollection("customers", Customer.class);
            if (nextSequenceId == null) {
                customers.watch().forEach(x -> {
                    sendToQueueService.sendToQueue(Objects.requireNonNull(x.getFullDocument()));
                    nextSequenceId = x.getResumeToken().toString();
                });
            } else {
                BsonValue value = new BsonString(nextSequenceId);
                BsonDocument bsonDocument = new BsonDocument("_data", value);
                List<Bson> pipeline = singletonList(match(in("operationType", asList("insert", "delete"))));
                customers.watch(pipeline).resumeAfter(bsonDocument)
                        .forEach(x -> {
                                    sendToQueueService.sendToQueue(Objects.requireNonNull(x.getFullDocument()));
                                    nextSequenceId = x.getResumeToken().toString();
                                }
                        );
            }

        }
    }


}
