//    implement cell line sensitity to drug exaxmple

package edu.ucsc

import java.text.DecimalFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;

import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import edu.umd.cs.psl.config.*
import edu.umd.cs.psl.database.DataStore
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabasePopulator;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.groovy.PredicateConstraint;
import edu.umd.cs.psl.groovy.SetComparison;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.ui.functions.textsimilarity.*
import edu.umd.cs.psl.ui.loading.InserterUtils;
import edu.umd.cs.psl.util.database.Queries;
import edu.umd.cs.psl.evaluation.statistics.*;


essen_weight = Double.parseDouble(this.args[0]);
active_weight = Double.parseDouble(this.args[1]);
prior_weight = Double.parseDouble(this.args[2]);

// psl initial setup
ConfigManager cm = ConfigManager.getManager()
ConfigBundle config = cm.getBundle("first-model")
def defaultPath = System.getProperty("java.io.tmpdir")
String dbpath = config.getString("dbpath", defaultPath + File.separator + "first-model")
DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, dbpath, true), config)

// define psl model 
PSLModel m = new PSLModel(this, data)
m.add predicate: "Drug",        types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "Gene",        types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "Cell",        types: [ArgumentType.UniqueID, ArgumentType.String]
m.add predicate: "DrugTarget",  types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Essential",   types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Active",      types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add predicate: "Sensitive",   types: [ArgumentType.UniqueID, ArgumentType.UniqueID]
m.add rule : ( DrugTarget(D, G) & Essential(C, G) ) >> Sensitive(C, D),  weight : essen_weight
m.add rule : ( DrugTarget(D, G) & Active(C, G) ) >> Sensitive(C, D),  weight : active_weight
m.add rule: ~Sensitive(C, D), weight: prior_weight 

println m;

// loads data
def dir = 'data'+java.io.File.separator +"first_model"+java.io.File.separator;
def target_dir = "seed0" + java.io.File.separator + "cross_val" + java.io.File.separator + "5fold" + java.io.File.separator;
def evidencePartition = new Partition(0);

insert = data.getInserter(Drug, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"drug.txt");
insert = data.getInserter(Gene, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"gene.txt");
insert = data.getInserter(Cell, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"cell.txt");
insert = data.getInserter(DrugTarget, evidencePartition);
InserterUtils.loadDelimitedData(insert, dir+"drug_target.txt");
insert = data.getInserter(Essential, evidencePartition);
InserterUtils.loadDelimitedDataTruth(insert, dir+"essential.txt");
insert = data.getInserter(Active, evidencePartition);
InserterUtils.loadDelimitedDataTruth(insert, dir+"active.txt");

def targetPartition = new Partition(1);
insert = data.getInserter(Sensitive, targetPartition);
InserterUtils.loadDelimitedData(insert, dir + "sensitive_target.txt");

Database db = data.getDatabase(targetPartition, [Drug, Gene, Cell, DrugTarget, Essential, Active] as Set, evidencePartition);

// run inference
println "run inference"
MPEInference inferenceApp = new MPEInference(m, db, config);
inferenceApp.mpeInference();
inferenceApp.close();

println "saving inference results"
DecimalFormat formatter = new DecimalFormat("#.#######");
def result_file = new File(args[3]);
result_file.write ""
for (GroundAtom atom : Queries.getAllAtoms(db, Sensitive)) {
    for (int i=0; i<2; i++) {
        result_file << atom.arguments[i].toString() + "\t"
    }
    result_file << formatter.format(atom.getValue()) + "\n"}

db.close();
