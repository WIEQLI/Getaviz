package org.getaviz.generator.jqa;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getaviz.generator.SettingsConfiguration;
import org.getaviz.lib.database.Database;
import org.getaviz.lib.database.Rels;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

public class JQACEnhancement {
	private final Log log = LogFactory.getLog(JQACEnhancement.class);
	private final SettingsConfiguration config = SettingsConfiguration.getInstance();
	private final GraphDatabaseService graph = Database.getInstance(config.getDatabaseName());
	
	public JQACEnhancement() {
		log.info("JQACEnhancement has started.");
		Transaction tx = graph.beginTx();
		
		try {
			addHashes();
			tx.success();
		} finally {
			tx.close();
		}
		
		log.info("JQACEnhancement finished");
	}
	
	private void addHashes() throws QueryExecutionException{
		List<Node> translationUnits = new ArrayList<>();
		Result queryResult = graph.execute("MATCH (n:TranslationUnit) RETURN n");
		while(queryResult.hasNext()) {
			translationUnits.add((Node)queryResult.next().get("n"));
		}
		
		for(Node translationUnit : translationUnits) {
			//build translationUnit name, fqn and hash from the filename of the containing file
			String fileName = translationUnit.getSingleRelationship(Rels.CONTAINS, Direction.INCOMING).getStartNode().getProperty("fileName").toString();
			if(!translationUnit.hasProperty("name")){
				translationUnit.setProperty("name", fileName);
			}
			if(!translationUnit.hasProperty("fqn")){
				translationUnit.setProperty("fqn", fileName);
			}
			if(!translationUnit.hasProperty("hash")){
				translationUnit.setProperty("hash", createHash(translationUnit.getProperty("fqn").toString()));
			}
			
			//build fqn and hash for functions etc. from the name of the translation unit plus their own name
			this.graph.traversalDescription().depthFirst().relationships(Rels.DECLARES, Direction.OUTGOING).traverse(translationUnit).nodes().forEach(child -> {
				if(!child.hasProperty("name")){
					child.setProperty("name", Long.toString(((Node)child).getId()));
				}
				if(!child.hasProperty("fqn")){
					child.setProperty("fqn", (fileName + "_" + child.getProperty("name")));
				}
				if(!child.hasProperty("hash")){
					child.setProperty("hash", createHash(child.getProperty("fqn").toString()));
				}
					
			});
		}
	}
	
	private String createHash(String fqn) {
		return "ID_" + DigestUtils.sha1Hex(fqn + config.getRepositoryName() + config.getRepositoryOwner());
	}
}
