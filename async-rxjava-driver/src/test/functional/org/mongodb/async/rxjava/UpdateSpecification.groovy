/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
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

package org.mongodb.async.rxjava

import org.mongodb.Document
import org.mongodb.WriteResult

import static org.mongodb.async.rxjava.Fixture.get
import static org.mongodb.async.rxjava.Fixture.getAsList

class UpdateSpecification extends FunctionalSpecification {
     def 'update should update all matching documents'() {
         given:
         def documents = [new Document('_id', 1).append('x', true), new Document('_id', 2).append('x', true)]
         get(collection.insert(documents))

         when:
         get(collection.find(new Document('x', true)).update(new Document('$set', new Document('y', false))))

         then:
         getAsList(collection.find(new Document('x', true)).sort(new Document('_id', 1)).forEach()) ==
         documents.each { it.append('y', false) }
     }

    def 'update with upsert should insert a single document if there are no matching documents'() {
        when:
        WriteResult result = get(collection.find(new Document('x', true)).upsert().update(new Document('$set', new Document('y', false))))

        then:
        getAsList(collection.find(new Document()).forEach()) ==
        [new Document('_id', result.upsertedId).append('x', true).append('y', false)]
    }

    def 'updateOne should update one matching document'() {
        given:
        def document = new Document('_id', 1).append('x', true)
        get(collection.insert(document))

        when:
        get(collection.find(new Document('x', true)).updateOne(new Document('$set', new Document('y', false))))

        then:
        getAsList(collection.find(new Document('y', false)).forEach()) == [document.append('y', false)]
    }

    def 'updateOne should update one of many matching documents'() {
        given:
        def documents = [new Document('_id', 1).append('x', true), new Document('_id', 2).append('x', true)]
        get(collection.insert(documents))

        when:
        get(collection.find(new Document('x', true)).updateOne(new Document('$set', new Document('y', false))))

        then:
        get(collection.find(new Document('y', false)).count()) == 1
    }

    def 'updateOne with upsert should insert a document if there are no matching documents'() {
        given:
        def document = new Document('x', true)

        when:
        def result = get(collection.find(new Document('x', true)).upsert().updateOne(new Document('$set', new Document('y', false))))

        then:
        getAsList(collection.find(new Document('y', false)).forEach()) ==
        [document.append('_id', result.getUpsertedId()).append('y', false)]
    }
}