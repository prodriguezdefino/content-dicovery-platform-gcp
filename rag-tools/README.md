# Content Discovery Platform - MCP Server

This project is an implementation of a **Model Context Protocol (MCP) server**.
It functions as the **QUERY service** of the [services module](../services/README.md) The primary goal of this MCP server is to provide a standardized and discoverable interface for an AI-powered content discovery platform to query and retrieve information.

By leveraging the MCP standard, this server allows various AI agents and tools to seamlessly interact with the underlying data of the "services project" without requiring custom integrations. It essentially acts as a bridge, translating natural language or structured queries from an MCP client into specific data retrieval actions within the content platform's ecosystem.

---

## How to run in dev mode

    ```bash
    mvn spring-boot:run -Dspring-boot.run.arguments="--secretmanager.configuration.version=projects/<project num>/secrets/<secret name>/versions/<version>"
    ```

## Next Steps

**Run the MCP client project:** With the MCP server running, you can now launch the corresponding [MCP client project](../agent/README.md). 
This client application will be used to send queries to the MCP server and verify its functionality. 
The client provides the interface to interact with the server's capabilities.