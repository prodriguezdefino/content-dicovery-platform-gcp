# Content Discovery Platform - MCP Client

This project is the **client-side implementation** for the Content Discovery Platform. 
Its purpose is to interact with the **MCP Server** and expose the QUERY service.


This client acts as the user-facing or programmatic interface to the content discovery system. 
It leverages the **Model Context Protocol (MCP)** to send queries to the server and render the retrieved information. 
It can be used as an AI-powered agent or a tool for users to search, explore, and interact with the platform's 
knowledge base in a standardized way.

---

## How to run for debugging
Prerequisite: run the [MCP server](../rag-tools/README.md) first.

1. Set the environment variables
    ```bash
   export GCP_PROJECT_ID="pso-dev-whaite"
   export MCP_SERVER_URL="http://localhost"
   ```
   
2. Run the client
    ```bash
    mvn spring-boot:run
   ```
   
3. Submit a query to the agent
    ```bash
    curl -d '{"q":"<add your question here>"}' \
   -H "Content-Type: application/json" -X POST http://localhost:8080/interact | jq .
   ```
