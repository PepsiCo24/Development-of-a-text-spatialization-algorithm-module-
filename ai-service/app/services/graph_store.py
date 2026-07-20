from collections import defaultdict
from typing import Any

from app.core.config import Settings, get_settings
from app.models.graph import GraphNode, GraphRelation

RELATION_TYPES = {"LOCATED_IN", "CONTAINS", "CONTROLS", "INTRUDES"}


class Neo4jGraphStore:
    def __init__(self, settings: Settings | None = None, driver=None) -> None:
        self.settings = settings or get_settings()
        if driver is None:
            from neo4j import GraphDatabase
            driver = GraphDatabase.driver(self.settings.neo4j_uri, auth=(self.settings.neo4j_username, self.settings.neo4j_password))
        self.driver = driver

    def sync(self, document_id: int, nodes: list[GraphNode], relations: list[GraphRelation]) -> tuple[int, int]:
        with self.driver.session(database=self.settings.neo4j_database) as session:
            session.run("MATCH ()-[r]->() WHERE r.documentId=$documentId DELETE r", documentId=document_id).consume()
            session.run("MATCH (n:GeologicalEntity {documentId:$documentId}) DETACH DELETE n", documentId=document_id).consume()
            if nodes:
                session.run("""UNWIND $nodes AS row MERGE (n:GeologicalEntity {entityId:row.entity_id})
                SET n.documentId=row.document_id,n.name=row.name,n.nodeType=row.node_type,n.sourceText=row.source_text,
                n.page=row.page,n.longitude=row.longitude,n.latitude=row.latitude""", nodes=[node.model_dump() for node in nodes]).consume()
            grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
            for relation in relations:
                grouped[relation.relation_type].append(relation.model_dump())
            for relation_type, rows in grouped.items():
                if relation_type not in RELATION_TYPES:
                    continue
                session.run(f"""UNWIND $rows AS row MATCH (s:GeologicalEntity {{entityId:row.source_entity_id}}),(t:GeologicalEntity {{entityId:row.target_entity_id}})
                MERGE (s)-[r:{relation_type} {{documentId:$documentId}}]->(t)
                SET r.confidence=row.confidence,r.sourceText=row.source_text,r.page=row.page""", rows=rows, documentId=document_id).consume()
        return len(nodes), len(relations)

    def nodes(self, query: str | None = None, limit: int = 100) -> dict[str, list[dict]]:
        where = "WHERE toLower(n.name) CONTAINS toLower($query)" if query else ""
        cypher = f"MATCH (n:GeologicalEntity) {where} RETURN n ORDER BY n.name LIMIT $limit"
        with self.driver.session(database=self.settings.neo4j_database) as session:
            nodes = [self._node(record["n"]) for record in session.run(cypher, query=query or "", limit=limit)]
        return {"nodes": nodes, "links": []}

    def expand(self, entity_id: int, depth: int = 1) -> dict[str, list[dict]]:
        depth = max(1, min(depth, 3))
        with self.driver.session(database=self.settings.neo4j_database) as session:
            records = list(session.run(f"MATCH p=(start:GeologicalEntity {{entityId:$entityId}})-[*1..{depth}]-(other:GeologicalEntity) RETURN p LIMIT 200", entityId=entity_id))
        return self._paths(records)

    def path(self, source_id: int, target_id: int) -> dict[str, list[dict]]:
        with self.driver.session(database=self.settings.neo4j_database) as session:
            records = list(session.run("MATCH p=shortestPath((s:GeologicalEntity {entityId:$sourceId})-[*..6]-(t:GeologicalEntity {entityId:$targetId})) RETURN p", sourceId=source_id, targetId=target_id))
        return self._paths(records)

    def context_for_documents(self, document_ids: list[int]) -> list[dict]:
        if not document_ids:
            return []
        with self.driver.session(database=self.settings.neo4j_database) as session:
            return [self._node(record["n"]) for record in session.run("MATCH (n:GeologicalEntity) WHERE n.documentId IN $ids RETURN n LIMIT 80", ids=document_ids)]

    @staticmethod
    def _node(node) -> dict:
        data = dict(node)
        return {"id":data.get("entityId"),"name":data.get("name"),"nodeType":data.get("nodeType"),"documentId":data.get("documentId"),"sourceText":data.get("sourceText"),"page":data.get("page"),"longitude":data.get("longitude"),"latitude":data.get("latitude")}

    def _paths(self, records) -> dict[str, list[dict]]:
        nodes: dict[int, dict] = {}; links: dict[tuple, dict] = {}
        for record in records:
            path = record["p"]
            for node in path.nodes:
                item = self._node(node); nodes[item["id"]] = item
            for relationship in path.relationships:
                source = self._node(relationship.start_node)["id"]; target = self._node(relationship.end_node)["id"]
                links[(source,target,relationship.type)] = {"source":source,"target":target,"relationType":relationship.type,"confidence":relationship.get("confidence"),"sourceText":relationship.get("sourceText"),"page":relationship.get("page")}
        return {"nodes":list(nodes.values()),"links":list(links.values())}
