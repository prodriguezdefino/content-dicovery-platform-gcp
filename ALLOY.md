# AlloyDB Setup
In order to leverage AlloyDB a manual setup of the pgvector extension is required. The table used to store embeddings 
must also be manually created. 

Both can be done with the below SQL statement:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE public.rag_embeddings (
  id TEXT PRIMARY KEY,
  embedding vector(768)
);
```

