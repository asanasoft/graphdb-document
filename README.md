# GraphDB Document

The goal of this document is to provide a framework for storing complex objects in graph databases (GDBs) implementing the TinkerPop 3 API. 

Why would we create such a project if we have NoSQL databases like MongoDB or CouchDB that can do the job? Well, it comes down to the following: While NoSQL databases like MongoDB allow the storing of complex JSON documents, it advocates a denormalized data structure by always storing related information in a single document whenever possible. By the same token, its best practices try to reduce repetition of the data at the same time which causes developers to think carefully how they want the data to be represented. This sometimes leads to overly complicated solutions (if the data is quite complex in nature) and eventually hit a design threshold (see Data Models Examples and Patterns section in the MongoDB documentation at https://docs.mongodb.com/manual/applications/data-models/).

In short, relationships require each document to “know” about each other. This is not a requirement of graph databases, therefore, we can logically separate data that needs to be together with data that enforces a relationship using the concept of Edges.

GraphDB Document allows you to model your documents as a GraphEntity, which represents a JSON document. However, the document it self is presisted as a set of Vertices and Edges. You are also allowed to *share* a GraphEntity instance with other instances of GraphEntity. This is akin to foreign key/values in an RDBMS. 

Currently, this is a work in progress. We will be adding examples covering several use cases.
