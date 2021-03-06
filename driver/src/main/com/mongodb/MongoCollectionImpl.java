/*
 * Copyright (c) 2008-2016 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.DeleteRequest;
import com.mongodb.bulk.IndexRequest;
import com.mongodb.bulk.InsertRequest;
import com.mongodb.bulk.UpdateRequest;
import com.mongodb.bulk.WriteRequest;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.CreateIndexOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.DropIndexOptions;
import com.mongodb.client.model.FindOneAndDeleteOptions;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.FindOptions;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.RenameCollectionOptions;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.operation.CountOperation;
import com.mongodb.operation.CreateIndexesOperation;
import com.mongodb.operation.DropCollectionOperation;
import com.mongodb.operation.DropIndexOperation;
import com.mongodb.operation.FindAndDeleteOperation;
import com.mongodb.operation.FindAndReplaceOperation;
import com.mongodb.operation.FindAndUpdateOperation;
import com.mongodb.operation.MixedBulkWriteOperation;
import com.mongodb.operation.RenameCollectionOperation;
import com.mongodb.session.ClientSession;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWrapper;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mongodb.assertions.Assertions.notNull;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class MongoCollectionImpl<TDocument> implements MongoCollection<TDocument> {
    private final MongoNamespace namespace;
    private final Class<TDocument> documentClass;
    private final ReadPreference readPreference;
    private final CodecRegistry codecRegistry;
    private final WriteConcern writeConcern;
    private final boolean retryWrites;
    private final ReadConcern readConcern;
    private final OperationExecutor executor;

    MongoCollectionImpl(final MongoNamespace namespace, final Class<TDocument> documentClass, final CodecRegistry codecRegistry,
                        final ReadPreference readPreference, final WriteConcern writeConcern, final boolean retryWrites,
                        final ReadConcern readConcern, final OperationExecutor executor) {
        this.namespace = notNull("namespace", namespace);
        this.documentClass = notNull("documentClass", documentClass);
        this.codecRegistry = notNull("codecRegistry", codecRegistry);
        this.readPreference = notNull("readPreference", readPreference);
        this.writeConcern = notNull("writeConcern", writeConcern);
        this.retryWrites = retryWrites;
        this.readConcern = notNull("readConcern", readConcern);
        this.executor = notNull("executor", executor);
    }

    @Override
    public MongoNamespace getNamespace() {
        return namespace;
    }

    @Override
    public Class<TDocument> getDocumentClass() {
        return documentClass;
    }

    @Override
    public CodecRegistry getCodecRegistry() {
        return codecRegistry;
    }

    @Override
    public ReadPreference getReadPreference() {
        return readPreference;
    }

    @Override
    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    @Override
    public ReadConcern getReadConcern() {
        return readConcern;
    }

    @Override
    public <NewTDocument> MongoCollection<NewTDocument> withDocumentClass(final Class<NewTDocument> clazz) {
        return new MongoCollectionImpl<NewTDocument>(namespace, clazz, codecRegistry, readPreference, writeConcern, retryWrites,
                readConcern, executor);
    }

    @Override
    public MongoCollection<TDocument> withCodecRegistry(final CodecRegistry codecRegistry) {
        return new MongoCollectionImpl<TDocument>(namespace, documentClass, codecRegistry, readPreference, writeConcern, retryWrites,
                readConcern, executor);
    }

    @Override
    public MongoCollection<TDocument> withReadPreference(final ReadPreference readPreference) {
        return new MongoCollectionImpl<TDocument>(namespace, documentClass, codecRegistry, readPreference, writeConcern, retryWrites,
                readConcern, executor);
    }

    @Override
    public MongoCollection<TDocument> withWriteConcern(final WriteConcern writeConcern) {
        return new MongoCollectionImpl<TDocument>(namespace, documentClass, codecRegistry, readPreference, writeConcern, retryWrites,
                readConcern, executor);
    }

    @Override
    public MongoCollection<TDocument> withReadConcern(final ReadConcern readConcern) {
        return new MongoCollectionImpl<TDocument>(namespace, documentClass, codecRegistry, readPreference, writeConcern, retryWrites,
                readConcern, executor);
    }

    @Override
    public long count() {
        return count(new BsonDocument(), new CountOptions());
    }

    @Override
    public long count(final Bson filter) {
        return count(filter, new CountOptions());
    }

    @Override
    public long count(final Bson filter, final CountOptions options) {
        return executeCount(null, filter, options);
    }

    @Override
    public long count(final ClientSession clientSession) {
        return count(clientSession, new BsonDocument());
    }

    @Override
    public long count(final ClientSession clientSession, final Bson filter) {
        return count(clientSession, filter, new CountOptions());
    }

    @Override
    public long count(final ClientSession clientSession, final Bson filter, final CountOptions options) {
        notNull("clientSession", clientSession);
        return executeCount(clientSession, filter, options);
    }

    private long executeCount(final ClientSession clientSession, final Bson filter, final CountOptions options) {
        CountOperation operation = new CountOperation(namespace)
                                           .filter(toBsonDocument(filter))
                                           .skip(options.getSkip())
                                           .limit(options.getLimit())
                                           .maxTime(options.getMaxTime(MILLISECONDS), MILLISECONDS)
                                           .collation(options.getCollation())
                                           .readConcern(readConcern);
        if (options.getHint() != null) {
            operation.hint(toBsonDocument(options.getHint()));
        } else if (options.getHintString() != null) {
            operation.hint(new BsonString(options.getHintString()));
        }
        return executor.execute(operation, readPreference, clientSession);
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(final String fieldName, final Class<TResult> resultClass) {
        return distinct(fieldName, new BsonDocument(), resultClass);
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(final String fieldName, final Bson filter, final Class<TResult> resultClass) {
        return createDistinctIterable(null, fieldName, filter, resultClass);
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(final ClientSession clientSession, final String fieldName,
                                                        final Class<TResult> resultClass) {
        return distinct(clientSession, fieldName, new BsonDocument(), resultClass);
    }

    @Override
    public <TResult> DistinctIterable<TResult> distinct(final ClientSession clientSession, final String fieldName, final Bson filter,
                                                        final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return createDistinctIterable(clientSession, fieldName, filter, resultClass);
    }

    private <TResult> DistinctIterable<TResult> createDistinctIterable(final ClientSession clientSession, final String fieldName,
                                                                       final Bson filter, final Class<TResult> resultClass) {
        return new DistinctIterableImpl<TDocument, TResult>(clientSession, namespace, documentClass, resultClass, codecRegistry,
                                                                   readPreference, readConcern, executor, fieldName, filter);
    }

    @Override
    public FindIterable<TDocument> find() {
        return find(new BsonDocument(), documentClass);
    }

    @Override
    public <TResult> FindIterable<TResult> find(final Class<TResult> resultClass) {
        return find(new BsonDocument(), resultClass);
    }

    @Override
    public FindIterable<TDocument> find(final Bson filter) {
        return find(filter, documentClass);
    }

    @Override
    public <TResult> FindIterable<TResult> find(final Bson filter, final Class<TResult> resultClass) {
        return createFindIterable(null, filter, resultClass);
    }

    @Override
    public FindIterable<TDocument> find(final ClientSession clientSession) {
        notNull("clientSession", clientSession);
        return find(clientSession, new BsonDocument(), documentClass);
    }

    @Override
    public <TResult> FindIterable<TResult> find(final ClientSession clientSession, final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return find(clientSession, new BsonDocument(), resultClass);
    }

    @Override
    public FindIterable<TDocument> find(final ClientSession clientSession, final Bson filter) {
        notNull("clientSession", clientSession);
        return find(clientSession, filter, documentClass);
    }

    @Override
    public <TResult> FindIterable<TResult> find(final ClientSession clientSession, final Bson filter,
                                                final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return createFindIterable(clientSession, filter, resultClass);
    }

    private <TResult> FindIterable<TResult> createFindIterable(final ClientSession clientSession, final Bson filter,
                                                               final Class<TResult> resultClass) {
        return new FindIterableImpl<TDocument, TResult>(clientSession, namespace, this.documentClass, resultClass, codecRegistry,
                readPreference, readConcern, executor, filter, new FindOptions());
    }

    @Override
    public AggregateIterable<TDocument> aggregate(final List<? extends Bson> pipeline) {
        return aggregate(pipeline, documentClass);
    }

    @Override
    public <TResult> AggregateIterable<TResult> aggregate(final List<? extends Bson> pipeline, final Class<TResult> resultClass) {
        return createAggregateIterable(null, pipeline, resultClass);
    }

    @Override
    public AggregateIterable<TDocument> aggregate(final ClientSession clientSession, final List<? extends Bson> pipeline) {
        return aggregate(clientSession, pipeline, documentClass);
    }

    @Override
    public <TResult> AggregateIterable<TResult> aggregate(final ClientSession clientSession, final List<? extends Bson> pipeline,
                                                          final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return createAggregateIterable(clientSession, pipeline, resultClass);
    }

    private <TResult> AggregateIterable<TResult> createAggregateIterable(final ClientSession clientSession,
                                                                         final List<? extends Bson> pipeline,
                                                                         final Class<TResult> resultClass) {
        return new AggregateIterableImpl<TDocument, TResult>(clientSession, namespace, documentClass, resultClass, codecRegistry,
                                                                    readPreference, readConcern, writeConcern, executor, pipeline);
    }

    @Override
    public ChangeStreamIterable<TDocument> watch() {
        return watch(Collections.<Bson>emptyList());
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(final Class<TResult> resultClass) {
        return watch(Collections.<Bson>emptyList(), resultClass);
    }

    @Override
    public ChangeStreamIterable<TDocument> watch(final List<? extends Bson> pipeline) {
        return watch(pipeline, documentClass);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(final List<? extends Bson> pipeline, final Class<TResult> resultClass) {
        return createChangeStreamIterable(null, pipeline, resultClass);
    }

    @Override
    public ChangeStreamIterable<TDocument> watch(final ClientSession clientSession) {
        return watch(clientSession, Collections.<Bson>emptyList(), documentClass);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(final ClientSession clientSession, final Class<TResult> resultClass) {
        return watch(clientSession, Collections.<Bson>emptyList(), resultClass);
    }

    @Override
    public ChangeStreamIterable<TDocument> watch(final ClientSession clientSession, final List<? extends Bson> pipeline) {
        return watch(clientSession, pipeline, documentClass);
    }

    @Override
    public <TResult> ChangeStreamIterable<TResult> watch(final ClientSession clientSession, final List<? extends Bson> pipeline,
                                                         final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return createChangeStreamIterable(clientSession, pipeline, resultClass);
    }

    private <TResult> ChangeStreamIterable<TResult> createChangeStreamIterable(final ClientSession clientSession,
                                                                               final List<? extends Bson> pipeline,
                                                                               final Class<TResult> resultClass) {
        return new ChangeStreamIterableImpl<TResult>(clientSession, namespace, codecRegistry, readPreference, readConcern, executor,
                pipeline, resultClass);
    }

    @Override
    public MapReduceIterable<TDocument> mapReduce(final String mapFunction, final String reduceFunction) {
        return mapReduce(mapFunction, reduceFunction, documentClass);
    }

    @Override
    public <TResult> MapReduceIterable<TResult> mapReduce(final String mapFunction, final String reduceFunction,
                                                          final Class<TResult> resultClass) {
        return createMapReduceIterable(null, mapFunction, reduceFunction, resultClass);
    }

    @Override
    public MapReduceIterable<TDocument> mapReduce(final ClientSession clientSession, final String mapFunction,
                                                  final String reduceFunction) {
        return mapReduce(clientSession, mapFunction, reduceFunction, documentClass);
    }

    @Override
    public <TResult> MapReduceIterable<TResult> mapReduce(final ClientSession clientSession, final String mapFunction,
                                                          final String reduceFunction, final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return createMapReduceIterable(clientSession, mapFunction, reduceFunction, resultClass);
    }

    private <TResult> MapReduceIterable<TResult> createMapReduceIterable(final ClientSession clientSession, final String mapFunction,
                                                                         final String reduceFunction, final Class<TResult> resultClass) {
        return new MapReduceIterableImpl<TDocument, TResult>(clientSession, namespace, documentClass, resultClass, codecRegistry,
                readPreference, readConcern, writeConcern, executor, mapFunction, reduceFunction);
    }

    @Override
    public BulkWriteResult bulkWrite(final List<? extends WriteModel<? extends TDocument>> requests) {
        return bulkWrite(requests, new BulkWriteOptions());
    }

    @Override
    public BulkWriteResult bulkWrite(final List<? extends WriteModel<? extends TDocument>> requests, final BulkWriteOptions options) {
        return executeBulkWrite(null, requests, options);
    }

    @Override
    public BulkWriteResult bulkWrite(final ClientSession clientSession, final List<? extends WriteModel<? extends TDocument>> requests) {
        return bulkWrite(clientSession, requests, new BulkWriteOptions());
    }

    @Override
    public BulkWriteResult bulkWrite(final ClientSession clientSession, final List<? extends WriteModel<? extends TDocument>> requests,
                                     final BulkWriteOptions options) {
        notNull("clientSession", clientSession);
        return executeBulkWrite(clientSession, requests, options);
    }

    @SuppressWarnings("unchecked")
    private BulkWriteResult executeBulkWrite(final ClientSession clientSession,
                                             final List<? extends WriteModel<? extends TDocument>> requests,
                                             final BulkWriteOptions options) {
        notNull("requests", requests);
        List<WriteRequest> writeRequests = new ArrayList<WriteRequest>(requests.size());
        for (WriteModel<? extends TDocument> writeModel : requests) {
            WriteRequest writeRequest;
            if (writeModel == null) {
                throw new IllegalArgumentException("requests can not contain a null value");
            } else if (writeModel instanceof InsertOneModel) {
                TDocument document = ((InsertOneModel<TDocument>) writeModel).getDocument();
                if (getCodec() instanceof CollectibleCodec) {
                    document = ((CollectibleCodec<TDocument>) getCodec()).generateIdIfAbsentFromDocument(document);
                }
                writeRequest = new InsertRequest(documentToBsonDocument(document));
            } else if (writeModel instanceof ReplaceOneModel) {
                ReplaceOneModel<TDocument> replaceOneModel = (ReplaceOneModel<TDocument>) writeModel;
                writeRequest = new UpdateRequest(toBsonDocument(replaceOneModel.getFilter()), documentToBsonDocument(replaceOneModel
                                                                                                                     .getReplacement()),
                                                        WriteRequest.Type.REPLACE)
                                       .upsert(replaceOneModel.getOptions().isUpsert())
                                       .collation(replaceOneModel.getOptions().getCollation());
            } else if (writeModel instanceof UpdateOneModel) {
                UpdateOneModel<TDocument> updateOneModel = (UpdateOneModel<TDocument>) writeModel;
                writeRequest = new UpdateRequest(toBsonDocument(updateOneModel.getFilter()), toBsonDocument(updateOneModel.getUpdate()),
                                                        WriteRequest.Type.UPDATE)
                                       .multi(false)
                                       .upsert(updateOneModel.getOptions().isUpsert())
                                       .collation(updateOneModel.getOptions().getCollation())
                                       .arrayFilters(toBsonDocumentList(updateOneModel.getOptions().getArrayFilters()));
            } else if (writeModel instanceof UpdateManyModel) {
                UpdateManyModel<TDocument> updateManyModel = (UpdateManyModel<TDocument>) writeModel;
                writeRequest = new UpdateRequest(toBsonDocument(updateManyModel.getFilter()), toBsonDocument(updateManyModel.getUpdate()),
                                                        WriteRequest.Type.UPDATE)
                                       .multi(true)
                                       .upsert(updateManyModel.getOptions().isUpsert())
                                       .collation(updateManyModel.getOptions().getCollation())
                                       .arrayFilters(toBsonDocumentList(updateManyModel.getOptions().getArrayFilters()));
            } else if (writeModel instanceof DeleteOneModel) {
                DeleteOneModel<TDocument> deleteOneModel = (DeleteOneModel<TDocument>) writeModel;
                writeRequest = new DeleteRequest(toBsonDocument(deleteOneModel.getFilter())).multi(false)
                                       .collation(deleteOneModel.getOptions().getCollation());
            } else if (writeModel instanceof DeleteManyModel) {
                DeleteManyModel<TDocument> deleteManyModel = (DeleteManyModel<TDocument>) writeModel;
                writeRequest = new DeleteRequest(toBsonDocument(deleteManyModel.getFilter())).multi(true)
                                       .collation(deleteManyModel.getOptions().getCollation());
            } else {
                throw new UnsupportedOperationException(format("WriteModel of type %s is not supported", writeModel.getClass()));
            }
            writeRequests.add(writeRequest);
        }

        return executor.execute(new MixedBulkWriteOperation(namespace, writeRequests, options.isOrdered(), writeConcern, retryWrites)
                                        .bypassDocumentValidation(options.getBypassDocumentValidation()), clientSession);
    }

    @Override
    public void insertOne(final TDocument document) {
        insertOne(document, new InsertOneOptions());
    }

    @Override
    public void insertOne(final TDocument document, final InsertOneOptions options) {
        notNull("document", document);
        executeInsertOne(null, document, options);
    }

    @Override
    public void insertOne(final ClientSession clientSession, final TDocument document) {
        insertOne(clientSession, document, new InsertOneOptions());
    }

    @Override
    public void insertOne(final ClientSession clientSession, final TDocument document, final InsertOneOptions options) {
        notNull("clientSession", clientSession);
        notNull("document", document);
        executeInsertOne(clientSession, document, options);
    }

    private void executeInsertOne(final ClientSession clientSession, final TDocument document, final InsertOneOptions options) {
        TDocument insertDocument = document;
        if (getCodec() instanceof CollectibleCodec) {
            insertDocument = ((CollectibleCodec<TDocument>) getCodec()).generateIdIfAbsentFromDocument(document);
        }
        executeSingleWriteRequest(clientSession, new InsertRequest(documentToBsonDocument(insertDocument)),
                options.getBypassDocumentValidation());
    }

    @Override
    public void insertMany(final List<? extends TDocument> documents) {
        insertMany(documents, new InsertManyOptions());
    }

    @Override
    public void insertMany(final List<? extends TDocument> documents, final InsertManyOptions options) {
        executeInsertMany(null, documents, options);
    }

    @Override
    public void insertMany(final ClientSession clientSession, final List<? extends TDocument> documents) {
        insertMany(clientSession, documents, new InsertManyOptions());
    }

    @Override
    public void insertMany(final ClientSession clientSession, final List<? extends TDocument> documents, final InsertManyOptions options) {
        notNull("clientSession", clientSession);
        executeInsertMany(clientSession, documents, options);
    }

    private void executeInsertMany(final ClientSession clientSession, final List<? extends TDocument> documents,
                                   final InsertManyOptions options) {
        notNull("documents", documents);
        List<InsertRequest> requests = new ArrayList<InsertRequest>(documents.size());
        for (TDocument document : documents) {
            if (document == null) {
                throw new IllegalArgumentException("documents can not contain a null value");
            }
            if (getCodec() instanceof CollectibleCodec) {
                document = ((CollectibleCodec<TDocument>) getCodec()).generateIdIfAbsentFromDocument(document);
            }
            requests.add(new InsertRequest(documentToBsonDocument(document)));
        }

        executor.execute(new MixedBulkWriteOperation(namespace, requests, options.isOrdered(), writeConcern, retryWrites)
                                 .bypassDocumentValidation(options.getBypassDocumentValidation()), clientSession);
    }

    @Override
    public DeleteResult deleteOne(final Bson filter) {
        return deleteOne(filter, new DeleteOptions());
    }

    @Override
    public DeleteResult deleteOne(final Bson filter, final DeleteOptions options) {
        return executeDelete(null, filter, options, false);
    }

    @Override
    public DeleteResult deleteOne(final ClientSession clientSession, final Bson filter) {
        return deleteOne(clientSession, filter, new DeleteOptions());
    }

    @Override
    public DeleteResult deleteOne(final ClientSession clientSession, final Bson filter, final DeleteOptions options) {
        notNull("clientSession", clientSession);
        return executeDelete(clientSession, filter, options, false);
    }

    @Override
    public DeleteResult deleteMany(final Bson filter) {
        return deleteMany(filter, new DeleteOptions());
    }

    @Override
    public DeleteResult deleteMany(final Bson filter, final DeleteOptions options) {
        return executeDelete(null, filter, options, true);
    }

    @Override
    public DeleteResult deleteMany(final ClientSession clientSession, final Bson filter) {
        return deleteMany(clientSession, filter, new DeleteOptions());
    }

    @Override
    public DeleteResult deleteMany(final ClientSession clientSession, final Bson filter, final DeleteOptions options) {
        notNull("clientSession", clientSession);
        return executeDelete(clientSession, filter, options, true);
    }

    @Override
    public UpdateResult replaceOne(final Bson filter, final TDocument replacement) {
        return replaceOne(filter, replacement, new UpdateOptions());
    }

    @Override
    public UpdateResult replaceOne(final Bson filter, final TDocument replacement, final UpdateOptions updateOptions) {
        return executeReplaceOne(null, filter, replacement, updateOptions);
    }

    @Override
    public UpdateResult replaceOne(final ClientSession clientSession, final Bson filter, final TDocument replacement) {
        return replaceOne(clientSession, filter, replacement, new UpdateOptions());
    }

    @Override
    public UpdateResult replaceOne(final ClientSession clientSession, final Bson filter, final TDocument replacement,
                                   final UpdateOptions updateOptions) {
        notNull("clientSession", clientSession);
        return executeReplaceOne(clientSession, filter, replacement, updateOptions);
    }

    private UpdateResult executeReplaceOne(final ClientSession clientSession, final Bson filter, final TDocument replacement,
                                           final UpdateOptions updateOptions) {
        return toUpdateResult(executeSingleWriteRequest(clientSession,
                new UpdateRequest(toBsonDocument(filter), documentToBsonDocument(replacement), WriteRequest.Type.REPLACE)
                        .upsert(updateOptions.isUpsert()).collation(updateOptions.getCollation()),
                updateOptions.getBypassDocumentValidation()));
    }

    @Override
    public UpdateResult updateOne(final Bson filter, final Bson update) {
        return updateOne(filter, update, new UpdateOptions());
    }

    @Override
    public UpdateResult updateOne(final Bson filter, final Bson update, final UpdateOptions updateOptions) {
        return executeUpdate(null, filter, update, updateOptions, false);
    }

    @Override
    public UpdateResult updateOne(final ClientSession clientSession, final Bson filter, final Bson update) {
        return updateOne(clientSession, filter, update, new UpdateOptions());
    }

    @Override
    public UpdateResult updateOne(final ClientSession clientSession, final Bson filter, final Bson update,
                                  final UpdateOptions updateOptions) {
        notNull("clientSession", clientSession);
        return executeUpdate(clientSession, filter, update, updateOptions, false);

    }

    @Override
    public UpdateResult updateMany(final Bson filter, final Bson update) {
        return updateMany(filter, update, new UpdateOptions());
    }

    @Override
    public UpdateResult updateMany(final Bson filter, final Bson update, final UpdateOptions updateOptions) {
        return executeUpdate(null, filter, update, updateOptions, true);
    }

    @Override
    public UpdateResult updateMany(final ClientSession clientSession, final Bson filter, final Bson update) {
        return updateMany(clientSession, filter, update, new UpdateOptions());
    }

    @Override
    public UpdateResult updateMany(final ClientSession clientSession, final Bson filter, final Bson update,
                                   final UpdateOptions updateOptions) {
        notNull("clientSession", clientSession);
        return executeUpdate(clientSession, filter, update, updateOptions, true);
    }

    @Override
    public TDocument findOneAndDelete(final Bson filter) {
        return findOneAndDelete(filter, new FindOneAndDeleteOptions());
    }

    @Override
    public TDocument findOneAndDelete(final Bson filter, final FindOneAndDeleteOptions options) {
        return executeFindOneAndDelete(null, filter, options);
    }

    @Override
    public TDocument findOneAndDelete(final ClientSession clientSession, final Bson filter) {
        return findOneAndDelete(clientSession, filter, new FindOneAndDeleteOptions());
    }

    @Override
    public TDocument findOneAndDelete(final ClientSession clientSession, final Bson filter, final FindOneAndDeleteOptions options) {
        notNull("clientSession", clientSession);
        return executeFindOneAndDelete(clientSession, filter, options);
    }

    private TDocument executeFindOneAndDelete(final ClientSession clientSession, final Bson filter, final FindOneAndDeleteOptions options) {
        return executor.execute(new FindAndDeleteOperation<TDocument>(namespace, writeConcern, retryWrites, getCodec())
                                        .filter(toBsonDocument(filter))
                                        .projection(toBsonDocument(options.getProjection()))
                                        .sort(toBsonDocument(options.getSort()))
                                        .maxTime(options.getMaxTime(MILLISECONDS), MILLISECONDS)
                                        .collation(options.getCollation()),
                clientSession);
    }

    @Override
    public TDocument findOneAndReplace(final Bson filter, final TDocument replacement) {
        return findOneAndReplace(filter, replacement, new FindOneAndReplaceOptions());
    }

    @Override
    public TDocument findOneAndReplace(final Bson filter, final TDocument replacement, final FindOneAndReplaceOptions options) {
        return executeFindOneAndReplace(null, filter, replacement, options);
    }

    @Override
    public TDocument findOneAndReplace(final ClientSession clientSession, final Bson filter, final TDocument replacement) {
        return findOneAndReplace(clientSession, filter, replacement, new FindOneAndReplaceOptions());
    }

    @Override
    public TDocument findOneAndReplace(final ClientSession clientSession, final Bson filter, final TDocument replacement,
                                       final FindOneAndReplaceOptions options) {
        notNull("clientSession", clientSession);
        return executeFindOneAndReplace(clientSession, filter, replacement, options);
    }

    private TDocument executeFindOneAndReplace(final ClientSession clientSession, final Bson filter, final TDocument replacement,
                                               final FindOneAndReplaceOptions options) {
        return executor.execute(new FindAndReplaceOperation<TDocument>(namespace, writeConcern, retryWrites, getCodec(),
                                                                              documentToBsonDocument(replacement))
                                        .filter(toBsonDocument(filter))
                                        .projection(toBsonDocument(options.getProjection()))
                                        .sort(toBsonDocument(options.getSort()))
                                        .returnOriginal(options.getReturnDocument() == ReturnDocument.BEFORE)
                                        .upsert(options.isUpsert())
                                        .maxTime(options.getMaxTime(MILLISECONDS), MILLISECONDS)
                                        .bypassDocumentValidation(options.getBypassDocumentValidation())
                                        .collation(options.getCollation()),
                clientSession);
    }

    @Override
    public TDocument findOneAndUpdate(final Bson filter, final Bson update) {
        return findOneAndUpdate(filter, update, new FindOneAndUpdateOptions());
    }

    @Override
    public TDocument findOneAndUpdate(final Bson filter, final Bson update, final FindOneAndUpdateOptions options) {
        return executeFindOneAndUpdate(null, filter, update, options);
    }

    @Override
    public TDocument findOneAndUpdate(final ClientSession clientSession, final Bson filter, final Bson update) {
        return findOneAndUpdate(clientSession, filter, update, new FindOneAndUpdateOptions());
    }

    @Override
    public TDocument findOneAndUpdate(final ClientSession clientSession, final Bson filter, final Bson update,
                                      final FindOneAndUpdateOptions options) {
        notNull("clientSession", clientSession);
        return executeFindOneAndUpdate(clientSession, filter, update, options);
    }

    private TDocument executeFindOneAndUpdate(final ClientSession clientSession, final Bson filter, final Bson update,
                                              final FindOneAndUpdateOptions options) {
        return executor.execute(new FindAndUpdateOperation<TDocument>(namespace, writeConcern, retryWrites, getCodec(),
                        toBsonDocument(update))
                                        .filter(toBsonDocument(filter))
                                        .projection(toBsonDocument(options.getProjection()))
                                        .sort(toBsonDocument(options.getSort()))
                                        .returnOriginal(options.getReturnDocument() == ReturnDocument.BEFORE)
                                        .upsert(options.isUpsert())
                                        .maxTime(options.getMaxTime(MILLISECONDS), MILLISECONDS)
                                        .bypassDocumentValidation(options.getBypassDocumentValidation())
                                        .collation(options.getCollation())
                                        .arrayFilters(toBsonDocumentList(options.getArrayFilters())),
                clientSession);
    }
    @Override
    public void drop() {
        executeDrop(null);
    }

    @Override
    public void drop(final ClientSession clientSession) {
        notNull("clientSession", clientSession);
        executeDrop(clientSession);
    }

    private void executeDrop(final ClientSession clientSession) {
        executor.execute(new DropCollectionOperation(namespace, writeConcern), clientSession);
    }

    @Override
    public String createIndex(final Bson keys) {
        return createIndex(keys, new IndexOptions());
    }

    @Override
    public String createIndex(final Bson keys, final IndexOptions indexOptions) {
        return createIndexes(singletonList(new IndexModel(keys, indexOptions))).get(0);
    }

    @Override
    public String createIndex(final ClientSession clientSession, final Bson keys) {
        return createIndex(clientSession, keys, new IndexOptions());
    }

    @Override
    public String createIndex(final ClientSession clientSession, final Bson keys, final IndexOptions indexOptions) {
        return createIndexes(clientSession, singletonList(new IndexModel(keys, indexOptions))).get(0);
    }

    @Override
    public List<String> createIndexes(final List<IndexModel> indexes) {
        return createIndexes(indexes, new CreateIndexOptions());
    }

    @Override
    public List<String> createIndexes(final List<IndexModel> indexes, final CreateIndexOptions createIndexOptions) {
        return executeCreateIndexes(null, indexes, createIndexOptions);
    }

    @Override
    public List<String> createIndexes(final ClientSession clientSession, final List<IndexModel> indexes) {
        return createIndexes(clientSession, indexes, new CreateIndexOptions());
    }

    @Override
    public List<String> createIndexes(final ClientSession clientSession, final List<IndexModel> indexes,
                                      final CreateIndexOptions createIndexOptions) {
        notNull("clientSession", clientSession);
        return executeCreateIndexes(clientSession, indexes, createIndexOptions);
    }

    private List<String> executeCreateIndexes(final ClientSession clientSession, final List<IndexModel> indexes,
                                              final CreateIndexOptions createIndexOptions) {
        notNull("indexes", indexes);
        notNull("createIndexOptions", createIndexOptions);
        List<IndexRequest> indexRequests = new ArrayList<IndexRequest>(indexes.size());
        for (IndexModel model : indexes) {
            if (model == null) {
                throw new IllegalArgumentException("indexes can not contain a null value");
            }
            indexRequests.add(new IndexRequest(toBsonDocument(model.getKeys()))
                                      .name(model.getOptions().getName())
                                      .background(model.getOptions().isBackground())
                                      .unique(model.getOptions().isUnique())
                                      .sparse(model.getOptions().isSparse())
                                      .expireAfter(model.getOptions().getExpireAfter(TimeUnit.SECONDS), TimeUnit.SECONDS)
                                      .version(model.getOptions().getVersion())
                                      .weights(toBsonDocument(model.getOptions().getWeights()))
                                      .defaultLanguage(model.getOptions().getDefaultLanguage())
                                      .languageOverride(model.getOptions().getLanguageOverride())
                                      .textVersion(model.getOptions().getTextVersion())
                                      .sphereVersion(model.getOptions().getSphereVersion())
                                      .bits(model.getOptions().getBits())
                                      .min(model.getOptions().getMin())
                                      .max(model.getOptions().getMax())
                                      .bucketSize(model.getOptions().getBucketSize())
                                      .storageEngine(toBsonDocument(model.getOptions().getStorageEngine()))
                                      .partialFilterExpression(toBsonDocument(model.getOptions().getPartialFilterExpression()))
                                      .collation(model.getOptions().getCollation())
            );
        }
        CreateIndexesOperation createIndexesOperation = new CreateIndexesOperation(getNamespace(), indexRequests, writeConcern)
                .maxTime(createIndexOptions.getMaxTime(MILLISECONDS), MILLISECONDS);
        executor.execute(createIndexesOperation, clientSession);
        return createIndexesOperation.getIndexNames();
    }

    @Override
    public ListIndexesIterable<Document> listIndexes() {
        return listIndexes(Document.class);
    }

    @Override
    public <TResult> ListIndexesIterable<TResult> listIndexes(final Class<TResult> resultClass) {
        return createListIndexesIterable(null, resultClass);
    }

    @Override
    public ListIndexesIterable<Document> listIndexes(final ClientSession clientSession) {
        return listIndexes(clientSession, Document.class);
    }

    @Override
    public <TResult> ListIndexesIterable<TResult> listIndexes(final ClientSession clientSession, final Class<TResult> resultClass) {
        notNull("clientSession", clientSession);
        return createListIndexesIterable(clientSession, resultClass);
    }

    private <TResult> ListIndexesIterable<TResult> createListIndexesIterable(final ClientSession clientSession,
                                                                             final Class<TResult> resultClass) {
        return new ListIndexesIterableImpl<TResult>(clientSession, getNamespace(), resultClass, codecRegistry, ReadPreference.primary(),
                executor);
    }

    @Override
    public void dropIndex(final String indexName) {
        dropIndex(indexName, new DropIndexOptions());
    }

    @Override
    public void dropIndex(final String indexName, final DropIndexOptions dropIndexOptions) {
        executeDropIndex(null, indexName, dropIndexOptions);
    }

    @Override
    public void dropIndex(final Bson keys) {
        dropIndex(keys, new DropIndexOptions());
    }

    @Override
    public void dropIndex(final Bson keys, final DropIndexOptions dropIndexOptions) {
        executeDropIndex(null, keys, dropIndexOptions);
    }

    @Override
    public void dropIndex(final ClientSession clientSession, final String indexName) {
        dropIndex(clientSession, indexName, new DropIndexOptions());
    }

    @Override
    public void dropIndex(final ClientSession clientSession, final Bson keys) {
        dropIndex(clientSession, keys, new DropIndexOptions());
    }

    @Override
    public void dropIndex(final ClientSession clientSession, final String indexName, final DropIndexOptions dropIndexOptions) {
        notNull("clientSession", clientSession);
        executeDropIndex(clientSession, indexName, dropIndexOptions);
    }

    @Override
    public void dropIndex(final ClientSession clientSession, final Bson keys, final DropIndexOptions dropIndexOptions) {
        notNull("clientSession", clientSession);
        executeDropIndex(clientSession, keys, dropIndexOptions);
    }

    @Override
    public void dropIndexes() {
        dropIndex("*");
    }

    @Override
    public void dropIndexes(final ClientSession clientSession) {
        notNull("clientSession", clientSession);
        executeDropIndex(clientSession, "*", new DropIndexOptions());
    }

    @Override
    public void dropIndexes(final DropIndexOptions dropIndexOptions) {
        dropIndex("*", dropIndexOptions);
    }

    @Override
    public void dropIndexes(final ClientSession clientSession, final DropIndexOptions dropIndexOptions) {
        dropIndex(clientSession, "*", dropIndexOptions);
    }

    private void executeDropIndex(final ClientSession clientSession, final String indexName, final DropIndexOptions dropIndexOptions) {
        notNull("dropIndexOptions", dropIndexOptions);
        executor.execute(new DropIndexOperation(namespace, indexName, writeConcern)
                .maxTime(dropIndexOptions.getMaxTime(MILLISECONDS), MILLISECONDS), clientSession);
    }

    private void executeDropIndex(final ClientSession clientSession, final Bson keys, final DropIndexOptions dropIndexOptions) {
        executor.execute(new DropIndexOperation(namespace, keys.toBsonDocument(BsonDocument.class, codecRegistry), writeConcern)
                        .maxTime(dropIndexOptions.getMaxTime(MILLISECONDS), MILLISECONDS), clientSession);
    }

    @Override
    public void renameCollection(final MongoNamespace newCollectionNamespace) {
        renameCollection(newCollectionNamespace, new RenameCollectionOptions());
    }

    @Override
    public void renameCollection(final MongoNamespace newCollectionNamespace, final RenameCollectionOptions renameCollectionOptions) {
        executeRenameCollection(null, newCollectionNamespace, renameCollectionOptions);
    }

    @Override
    public void renameCollection(final ClientSession clientSession, final MongoNamespace newCollectionNamespace) {
        renameCollection(clientSession, newCollectionNamespace, new RenameCollectionOptions());
    }

    @Override
    public void renameCollection(final ClientSession clientSession, final MongoNamespace newCollectionNamespace,
                                 final RenameCollectionOptions renameCollectionOptions) {
        notNull("clientSession", clientSession);
        executeRenameCollection(clientSession, newCollectionNamespace, renameCollectionOptions);
    }

    private void executeRenameCollection(final ClientSession clientSession, final MongoNamespace newCollectionNamespace,
                                         final RenameCollectionOptions renameCollectionOptions) {
        executor.execute(new RenameCollectionOperation(getNamespace(), newCollectionNamespace, writeConcern)
                                 .dropTarget(renameCollectionOptions.isDropTarget()),
                clientSession);
    }

    private DeleteResult executeDelete(final ClientSession clientSession, final Bson filter, final DeleteOptions deleteOptions,
                                       final boolean multi) {
        com.mongodb.bulk.BulkWriteResult result = executeSingleWriteRequest(clientSession,
                new DeleteRequest(toBsonDocument(filter)).multi(multi)
                        .collation(deleteOptions.getCollation()), null);
        if (result.wasAcknowledged()) {
            return DeleteResult.acknowledged(result.getDeletedCount());
        } else {
            return DeleteResult.unacknowledged();
        }
    }

    private UpdateResult executeUpdate(final ClientSession clientSession, final Bson filter, final Bson update,
                                       final UpdateOptions updateOptions, final boolean multi) {
        return toUpdateResult(executeSingleWriteRequest(clientSession, new UpdateRequest(toBsonDocument(filter), toBsonDocument(update),
                WriteRequest.Type.UPDATE).upsert(updateOptions.isUpsert()).multi(multi).collation(updateOptions.getCollation())
                                         .arrayFilters(toBsonDocumentList(updateOptions.getArrayFilters())),
                updateOptions.getBypassDocumentValidation()));
    }


    private BulkWriteResult executeSingleWriteRequest(final ClientSession clientSession, final WriteRequest request,
                                                      final Boolean bypassDocumentValidation) {
        try {
            return executor.execute(new MixedBulkWriteOperation(namespace, asList(request), true, writeConcern, retryWrites)
                    .bypassDocumentValidation(bypassDocumentValidation), clientSession);
        } catch (MongoBulkWriteException e) {
            if (e.getWriteErrors().isEmpty()) {
                throw new MongoWriteConcernException(e.getWriteConcernError(),
                                                     translateBulkWriteResult(request, e.getWriteResult()),
                                                     e.getServerAddress());
            } else {
                throw new MongoWriteException(new WriteError(e.getWriteErrors().get(0)), e.getServerAddress());
            }
        }
    }

    private WriteConcernResult translateBulkWriteResult(final WriteRequest request, final BulkWriteResult writeResult) {
        switch (request.getType()) {
            case INSERT:
                return WriteConcernResult.acknowledged(writeResult.getInsertedCount(), false, null);
            case DELETE:
                return WriteConcernResult.acknowledged(writeResult.getDeletedCount(), false, null);
            case UPDATE:
            case REPLACE:
                return WriteConcernResult.acknowledged(writeResult.getMatchedCount() + writeResult.getUpserts().size(),
                                                       writeResult.getMatchedCount() > 0,
                                                       writeResult.getUpserts().isEmpty()
                                                       ? null : writeResult.getUpserts().get(0).getId());
            default:
                throw new MongoInternalException("Unhandled write request type: " + request.getType());
        }
    }

    private UpdateResult toUpdateResult(final com.mongodb.bulk.BulkWriteResult result) {
        if (result.wasAcknowledged()) {
            Long modifiedCount = result.isModifiedCountAvailable() ? (long) result.getModifiedCount() : null;
            BsonValue upsertedId = result.getUpserts().isEmpty() ? null : result.getUpserts().get(0).getId();
            return UpdateResult.acknowledged(result.getMatchedCount(), modifiedCount, upsertedId);
        } else {
            return UpdateResult.unacknowledged();
        }
    }

    private Codec<TDocument> getCodec() {
        return codecRegistry.get(documentClass);
    }

    private BsonDocument documentToBsonDocument(final TDocument document) {
        return BsonDocumentWrapper.asBsonDocument(document, codecRegistry);
    }

    private BsonDocument toBsonDocument(final Bson bson) {
        return bson == null ? null : bson.toBsonDocument(documentClass, codecRegistry);
    }

    private List<BsonDocument> toBsonDocumentList(final List<? extends Bson> bsonList) {
        if (bsonList == null) {
            return null;
        }
        List<BsonDocument> bsonDocumentList = new ArrayList<BsonDocument>(bsonList.size());
        for (Bson cur : bsonList) {
            bsonDocumentList.add(toBsonDocument(cur));
        }
        return bsonDocumentList;
    }
}
