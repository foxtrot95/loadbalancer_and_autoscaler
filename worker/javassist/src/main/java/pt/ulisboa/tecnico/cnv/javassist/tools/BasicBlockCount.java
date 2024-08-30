package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class BasicBlockCount extends AbstractJavassistTool {

    
    /**
     * Maps method long names to a map of basic block positions to the number of executions.
     */
    private static Map<String, Map<Integer, Integer>> counters = new HashMap<>();

    public BasicBlockCount(List<String> packageNameList, String writeDestination) {
        super(packageNameList,writeDestination);
    }

    public static void visitBasicBlock(String methodLongName, int position) {

        if (!counters.containsKey(methodLongName)) {
            counters.put(methodLongName, new HashMap<>());
        }

        if (!counters.get(methodLongName).containsKey(position)) {
            counters.get(methodLongName).put(position, 0);
        }

        counters.get(methodLongName).put(position, counters.get(methodLongName).get(position) + 1);
    }


    public static void printStatistics() {
        int nblocks = 0; 
        for (Map.Entry<String, Map<Integer, Integer>> method : counters.entrySet()) {
            for (Map.Entry<Integer, Integer> basicblock : method.getValue().entrySet()) {
                nblocks += basicblock.getValue(); 
                //System.out.println(String.format("[%s] %s basic block %s was called %s times", BasicBlockCount.class.getSimpleName(), method.getKey(), basicblock.getKey(), basicblock.getValue()));
            }
        }

        System.out.println(String.format("Number of instructed basic blocks: %s", nblocks));
        
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);

        if (behavior.getName().equals("handleRequest")) {
            behavior.insertAfter(String.format("%s.printStatistics();", BasicBlockCount.class.getName()));
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.visitBasicBlock(\"%s\", %s);", BasicBlockCount.class.getName(), block.getBehavior().getLongName(), block.getPosition()));
    }
}