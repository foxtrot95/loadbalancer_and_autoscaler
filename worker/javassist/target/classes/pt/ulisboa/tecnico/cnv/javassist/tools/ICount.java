package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class ICount extends AbstractJavassistTool {

    private static Map<Long, Long> map_nblocks = new HashMap<>(); 
    private static Map<Long, Long> map_nmethods = new HashMap<>(); 
    private static Map<Long, Long> map_ninsts = new HashMap<>(); 

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length) {
        Long current_nblocks = map_nblocks.get(Thread.currentThread().getId());
        Long current_ninsts = map_ninsts.get(Thread.currentThread().getId());
        if(current_nblocks != null & current_ninsts != null) {
            map_nblocks.put(Thread.currentThread().getId(), current_nblocks + 1);
            map_ninsts.put(Thread.currentThread().getId(), current_ninsts + length);
        }
    }

    public static void incBehavior(String name) {
        Long current_nmethods = map_nmethods.get(Thread.currentThread().getId());
        if(current_nmethods != null) {
            map_nmethods.put(Thread.currentThread().getId(), current_nmethods + 1);
        }
    }

    public static void resetStatistics() {
        map_nblocks.put(Thread.currentThread().getId(), 0L);
        map_ninsts.put(Thread.currentThread().getId(), 0L);
        map_nmethods.put(Thread.currentThread().getId(), 0L);
    }

    public static Long[] getStatistics() {
        Long nblocks = map_nblocks.get(Thread.currentThread().getId());
        Long ninsts = map_ninsts.get(Thread.currentThread().getId());
        Long nmethods = map_nmethods.get(Thread.currentThread().getId());
        Long[] statistics = { ninsts, nblocks, nmethods};
        return statistics; 
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);

        if (behavior.getName().equals("sleep") && behavior.getSignature().equals("(J)V")) {
            return;
        }
        try{
            behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));
        } catch(CannotCompileException e) {
        }
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        if (block.behavior.getName().equals("sleep")) return;
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(), block.getPosition(), block.getLength()));
    }

}
