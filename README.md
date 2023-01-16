# Squerall
An implementation of the so-called Semantic Data Lake, using Apache Spark and Presto. Semantic Data Lake is a Data Lake accessed using Semantic Web technologies: ontologies and query language (SPARQL).

Currently supported data sources:
-[x] CSV
-[x] Parquet
-[x] MongoDB
-[x] Cassandra
-[x] JDBC


To get an understanding of Squerall basics, which also helps understand the installation steps hereafter, we need to understand the concepts below:

### 1. Mapping Language and Data Lake Schema
A virtual schema is added to the Data Lake by _mapping_ data elements, e.g., tables and attributes to ontology concepts, e.g., classes and predicates. We benefit from [RML](http://rml.io/) mappings to express those schema mapping links.

An example of such mappings is given below. It maps a collection named _Product_ (`rml:source "Product"`) in a MongoDB database to an ontology class _Product_ (`rr:class bsbm:Product`), meaning that every documebt in Product document is of type `bsbm:Product`. The mappings also link MongoDB collection fields `label`, `publisher` and `producer` to ontology predicates `rdfs:label`, `dc:publisher` and `bsbm:producer`, respectively. The `_id` field found in `rr:subjectMap rr:template "http://example.com/{_id}"` triple points to the primary key of MongoDB collection.

```
<#OfferMapping>
	rml:logicalSource [
		rml:source "//Offer";
		nosql:store nosql:Mongodb
	];
	rr:subjectMap [
		rr:template "http://example.com/{_id}";
		rr:class schema:Offer
	];

	rr:predicateObjectMap [
		rr:predicate bsbm:validTo;
		rr:objectMap [rml:reference "validTo"]
	];

	rr:predicateObjectMap [
		rr:predicate dc:publisher;
		rr:objectMap [rml:reference "publisher"]
	];

	rr:predicateObjectMap [
		rr:predicate bsbm:producer;
		rr:objectMap [rml:reference "producer"]
	];
```

Note the presence of the triple `nosql:store nosql:MongoDB`, it contains an addition to RML mappings from the [NoSQL ontology](http://purl.org/db/nosql#) to allow stating what type of source it is being mapped.

_The mappings file can either be created manually or using the following graphical utility: [Squerall-GUI](https://github.com/EIS-Bonn/Squerall-GUI)_.

### 2. Data Connection Configurations
In order for data to connect to a data source, users need to provide a set of config parameters, in JSON format. This differs from data source to another, for example for a MongoDB collection, the config parameters could be: database host URL, database name, collection name, and replica set name.

```JSON
{
  "type": "mongodb",
  "options": {
    "url": "127.0.0.1",
    "database": "bsbm",
    "collection": "offer",
    "options": "replicaSet=mongo-rs"
  },
  "source": "//Offer",
  "entity": "Offer"
}
```

It is necessary to link the configured source (`"source": "//Offer"`)  to the mapped source (`rml:logicalSource rml:source "//Offer"`, see Mapping section above)

_The config file can either be created manually or using the following graphical utility: [Squerall-GUI](https://github.com/EIS-Bonn/Squerall-GUI)_.

### 3. SPARQL Query Interface
SPARQL queries are expressed using the Ontology terms the data was previously mapped to. SPARQL query should conform to the currently supported SPARQL fragment:

```SPARQL
Query       := Prefix* SELECT Distinguish WHERE{ Clauses } Modifiers?
Prefix      := PREFIX "string:" IRI
Distinguish := DISTINCT? (“*”|(Var|Aggregate)+)
Aggregate   := (AggOpe(Var) ASVar)
AggOpe      := SUM|MIN|MAX|AVG|COUNT
Clauses     := TP* Filter?
Filter      := FILTER (Var FiltOpe Litteral)
             | FILTER regex(Var, "%string%")
FiltOpe     :==|!=|<|<=|>|>=
TP          := VarIRIVar .|Varrdf:type IRI.
Var         := "?string"
Modifiers   := (LIMITk)? (ORDER BY(ASC|DESC)? Var)? (GROUP BYVar+)?
```

The following query operations are currently not supported:
* Sub-queries.
* Object-to-object join.
* Filter between _object_ variables (e.g., `FILTER (obj1 = obj2)`).
* Aggregation on the _subject_ variable.
* Join RDF and Non-RDF data on the RDF subject position (i.e., ajoin plain with URI).
* When using a SPARQL query with only one star, add the object variable to SELECT.


### 4. Data Transformations for joins
Data from different data sources may not be readily joinable. Squerall allows the users to declare transformations that are executed on the fly on query-time on join keys. Depending on whether the transformations change in a query-to-query basis on once for all, users have the option to declare transformations at the mapping level or query level.

#### 4.1 Mappings level
Combining FNO along RML mappings allows users to declare that certain data values are not directly mapped to an ontology term, but first transformed using FNO function. For example take the last `rr:predicateObjectMap` and change it as follows:
```
rr:predicateObjectMap [
		rr:predicate <#FunctionMap>;
		rr:objectMap [rml:reference "producer"]
	];
```
Next:
```
<#FunctionMap>
 fnml:functionValue [
   rml:logicalSource "/root/data/review.parquet" ;

   rr:predicateObjectMap [
     rr:predicate fno:executes ;
     rr:objectMap [rr:constant grel:scale] ] ;

   rr:predicateObjectMap [
     rr:predicate grel:valueParam1 ;
     rr:objectMap [rr:reference "producer"]
   ] ;
   
   rr:predicateObjectMap [
     rr:predicate grel:valueParam2 ;
     rr:objectMap [rr:reference "123"]
   ] ;

] .
```

#### 4.2 Query level (experimental)
Users add a clause at the very end of the basic graph patter (BGP), in  this way:
`TRANSFORM(?leftJoinVariable.[l/r].[transformation]+`. For example: `?author?book.r.scl(123)`, it instructs Squerall to scale all join values of the left star (e.g., author.hasBook) by 123. If it was `?author?book.r.scl(123)`, i.e. `.l` instead, then apply the transformation(s) on the ID of the right star. The list of available transformations is as follows:
- `scl(int)` scale the join values up or down with a certain numerical value.
- skp(val)` skip a value, so no join using it is possible.
- `substit(val1,val2)` substitute the value `val1` with `val2` whenever encountered in the join values.
- `replc(val1,val2)` replace a substring in the join value with another substring.
- `prefix(val)` add a prefix to every join value.
- `postfix(val)` add a postfix to every join valrue.

Instructing to scale all values of attribute `Producer` with value 123. The same list of transformation as previously is possible, use the transformation name with the 'grel' namespace, e.g. `grel:prefix`.

## Setup and Execution

*- Prerequisite:* You need Maven to build Squerall from the source.
[x] Development was done in Ubuntu 20.04 machine.
[x] We used JDK 11 to develop and run the application.
[x] Scala version 2.12 need to compile the source code.
Refer to the official documentations for installation instructions: [Maven](https://maven.apache.org/install.html) run:
```
git clone https://github.com/logicalfarhad/Squerall.git
cd squerall
mvn package
cd target
```
...by default, you find a *squerall-1.0.0.jar* file in target forlder.

Squerall uses Spark as a query engine. User can run Spark in a single node, or deploy them in a cluster.
### Spark
- Spark 3.2.3 version
- Download Spark from the [Spark official website](https://spark.apache.org/downloads.html).
- In order for Spark to run in a cluster, you need to configure a 'standalone cluster' following guidelines in the [Spark installation guide](https://computingforgeeks.com/how-to-install-apache-spark-on-ubuntu-debian/).
### Hadoop
- Hadoop 3.2.4 version
- Download Hadoop from the [Spark hadoop website](https://hadoop.apache.org/releases.html).
- You can follow this link to install Hadoop for Ubuntu 20.04 [Hadoop installation guide](https://www.digitalocean.com/community/tutorials/how-to-install-hadoop-in-stand-alone-mode-on-ubuntu-20-04).



To run sparquell in spark cluster you can use spark-submit option: The command line looks like:
`/bin/spark-submit --class [Main classpath] --master [master URI] --executor-memory [memory reserved to the app] [path to squerall-1.0.0.jar] [query file] [mappings file] [config file] [master URI] n s`

- #### Example:
`/bin/spark-submit --class org.squerall.Main --master spark://127.140.106.146:3077 --executor-memory 250G squerall-1.0.0.jar query.sparql mappings.ttl config spark://172.14.160.146:3077 n s`

[x] query file: A file containing a correct SPARQL query, only.
[x] mappings file: A file contains RML mappings linking data to ontology terms (classes and properties), in JSON format.
[x] config file: A file containing information about how to access data sources (eg. host, user, password), in JSON format.

License
-------

This project is openly shared under the terms of the __Apache License
v2.0__ ([read for more](./LICENSE)).
