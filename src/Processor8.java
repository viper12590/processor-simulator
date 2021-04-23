import java.io.IOException;
import java.util.*;

public class Processor8 {

    public Processor8(int[] mem, Instruction[] instructions) {
        this.mem = mem;
        this.instructions = instructions;
    }

    int cycle = 0;
    int pc = 0; // Program counter
    int superScalarWidth = 4;
    int executedInsts = 0; // Number of instructions executed
    int stalledCycle = 0;
    int waitingCycle = 0;
    int predictedBranches = 0;
    int correctPrediction = 0;
    int misprediction = 0;
    int insIdCount = 1; // for assigning id to instructions
    int[] mem; // memory from user
    int[] rf = new int[64]; //Register file (physical)
    RegisterStatus[] regStats = new RegisterStatus[rf.length];
    Instruction[] instructions; // instructions from user
    boolean beforeFinish = false;
    boolean finished = false;
    int QUEUE_SIZE = 4;
    int ISSUE_SIZE = 16;
    Queue<Instruction> fetchedQueue = new LinkedList<>();
    Queue<Instruction> decodedQueue = new LinkedList<>();
    ReservationStation[] RS = new ReservationStation[ISSUE_SIZE * 2]; // unified reservation station
    CircularBufferROB ROB = new CircularBufferROB(ISSUE_SIZE * 4); // Reorder buffer
    Queue<Instruction> loadBuffer = new LinkedList<>();
    // final result registers before write back
    Queue<Instruction> beforeWriteBack = new LinkedList<>();
    Map<Integer,BTBstatus> BTB = new HashMap<>(); // 2-bit Branch Target Buffer, Key: insAddress, Value: BTB status

    int rs_aluReady = -1;
    int rs_aluReady2 = -1;
    int rs_loadReady = -1;
    int rs_storeReady = -1;
    int rs_bruReady = -1;
    int rs_otherReady = -1;

    // state of pipeline stages
    // fetch states
    boolean fetchBlocked = false;
    // decode states
    boolean nothingToDecode = false;
    boolean decodeBlocked = false;
    boolean branchPredicted = false;
    // issue states
    boolean nothingToIssue = false;
    boolean issueBlocked = false;
    // dispatch states
    boolean noReadyInstruction = false;
    // execute states
    boolean nothingToExecute = false;
    boolean executeBlocked = false;
    boolean euAllBusy = false;
    // memory states
    boolean nothingToMemory = false;
    // write back states
    boolean nothingToWriteBack = false;
    boolean writeBackBufferFull = false;
    // commit states
    boolean robEmpty = false;
    boolean commitUnavailable = false;

    boolean loadBufferFull = false;

    int predictionLayerLimit = 1;
    int speculativeExecution = 0;

    // execution units
    // Arithmetic Logic Unit
    ALU alu0 = new ALU();
    ALU alu1 = new ALU();
    // Address Generation Unit
    ALU agu = new ALU();
    // Branch Unit
    BRU bru0 = new BRU();

    //For visualisation
    List<Instruction> finishedInsts = new ArrayList<>();
    List<Probe> probes = new ArrayList<>();

    private void Fetch() {

        fetchBlocked = !fetchedQueue.isEmpty();
        if(!fetchBlocked && pc < instructions.length && !beforeFinish) {
            for(int x = 0; x < superScalarWidth; x++) {
                fetchLogic();
            }
        }
        if(fetchBlocked) { // stall can't fetch because the buffer is full
            probes.add(new Probe(cycle,0,0));
        }


//        for(int x=0; x < superScalarWidth; x++) {
//            fetchBlocked = fetchedQueue.size() >= QUEUE_SIZE;
//            if(!fetchBlocked && pc < instructions.length && !beforeFinish) {
//                fetchLogic();
//            }
//
//            if(fetchBlocked) { // stall can't fetch because the buffer is full
//                probes.add(new Probe(cycle,0,0));
//            }
//        }
    }

    private void fetchLogic() {
        Instruction fetch = instructions[pc];
        Instruction ins = new Instruction(); // NOOP
        if(fetch != null) {
            ins = new Instruction(fetch);
        }
        ins.id = insIdCount; // assign id
        ins.insAddress = pc; // assign ins address
        ins.fetchComplete = cycle; // save cycle number of fetch stage
        fetchedQueue.add(ins);
        pc++;
        insIdCount++; // prepare next id

        finishedInsts.add(ins);
    }

    // static predictor (take backward branch, deny forward branch)
    private boolean branchPredictor(Instruction ins) {
        return ins.Const <= pc;
    }

    private void Decode() {

        decodeBlocked = !decodedQueue.isEmpty();
        nothingToDecode = fetchedQueue.isEmpty();
        if(!decodeBlocked && !nothingToDecode) {
            for(int x = 0; x < superScalarWidth; x++) {
                if(branchPredicted) {
                    break;
                }
                decodeLogic();
            }
        }
        branchPredicted = false;


        if(decodeBlocked) { // stall: can't decode because the buffer is full
            Instruction ins = fetchedQueue.peek();
            if(ins != null) {
                probes.add(new Probe(cycle,2,ins.id));
            }
        }
        if(nothingToDecode) {
            probes.add(new Probe(cycle,1,0));
        }
        /*
        for(int x=0; x < superScalarWidth; x++) {
            decodeBlocked = decodedQueue.size() >= QUEUE_SIZE;
            nothingToDecode = fetchedQueue.isEmpty();
            if(!decodeBlocked && !nothingToDecode) {
                boolean unconditionalBranchTaken = false;
                Instruction decoded = fetchedQueue.remove();
                OpType opType = assignOpType(decoded.opcode);
                if(opType.equals(OpType.UNDEFINED)) {
                    System.out.println("invalid opcode detected while decoding");
                    finished = true;
                    return;
                }
                decoded.opType = opType;
                if(decoded.Rs1 == 0 && decoded.opcode.equals(Opcode.BR)) {
                    pc =  decoded.Const;
                    unconditionalBranchTaken = true;
                    fetchedQueue.clear();
                }
                if(decoded.opcode.equals(Opcode.JMP)) {
                    pc += decoded.Const;
                    unconditionalBranchTaken = true;
                    fetchedQueue.clear();
                }
                if(decoded.opcode.equals(Opcode.BRZ) || decoded.opcode.equals(Opcode.BRN)) {
                    BTBstatus btbCondition = BTB.get(decoded.insAddress);
                    decoded.predicted = true;
                    probes.add(new Probe(cycle,14,decoded.id));
                    if(btbCondition == null) {
                        if(branchPredictor(decoded)) {
                            BTB.put(decoded.insAddress,BTBstatus.YES);
                            pc = decoded.Const;
                            decoded.taken = true;
                            fetchedQueue.clear();
                        }
                        else {
                            BTB.put(decoded.insAddress,BTBstatus.NO);
                        }
                    }
                    else if(btbCondition.equals(BTBstatus.YES) || btbCondition.equals(BTBstatus.STRONG_YES)) {
                        pc = decoded.Const;
                        decoded.taken = true;
                        fetchedQueue.clear();
                    }
                    speculativeExecution++;
                    predictedBranches++;
                }

                decoded.decodeComplete = cycle; // save cycle number of decode stage
                int i = finishedInsts.indexOf(decoded);
                finishedInsts.set(i,decoded);
                if(!unconditionalBranchTaken) {
                    decodedQueue.add(decoded);
                }
            }

            if(decodeBlocked) { // stall: can't decode because the buffer is full
                Instruction ins = fetchedQueue.peek();
                if(ins != null) {
                    probes.add(new Probe(cycle,2,ins.id));
                }
            }
            if(nothingToDecode) {
                probes.add(new Probe(cycle,1,0));
            }
        }
         */
    }

    private void decodeLogic() {
        boolean unconditionalBranchTaken = false;
        Instruction decoded = fetchedQueue.remove();
        OpType opType = assignOpType(decoded.opcode);
        if(opType.equals(OpType.UNDEFINED)) {
            System.out.println("invalid opcode detected while decoding");
            finished = true;
            return;
        }
        decoded.opType = opType;
        if(decoded.Rs1 == 0 && decoded.opcode.equals(Opcode.BR)) {
            pc =  decoded.Const;
            unconditionalBranchTaken = true;
            fetchedQueue.clear();
            branchPredicted = true;
        }
        if(decoded.opcode.equals(Opcode.JMP)) {
            pc += decoded.Const;
            unconditionalBranchTaken = true;
            fetchedQueue.clear();
            branchPredicted = true;
        }
        if(decoded.opcode.equals(Opcode.BRZ) || decoded.opcode.equals(Opcode.BRN)) {
            BTBstatus btbCondition = BTB.get(decoded.insAddress);
            decoded.predicted = true;
            probes.add(new Probe(cycle,14,decoded.id));
            if(btbCondition == null) {
                if(branchPredictor(decoded)) {
                    BTB.put(decoded.insAddress,BTBstatus.YES);
                    pc = decoded.Const;
                    decoded.taken = true;
                    fetchedQueue.clear();
                    branchPredicted = true;
                }
                else {
                    BTB.put(decoded.insAddress,BTBstatus.NO);
                }
            }
            else if(btbCondition.equals(BTBstatus.YES) || btbCondition.equals(BTBstatus.STRONG_YES)) {
                pc = decoded.Const;
                decoded.taken = true;
                fetchedQueue.clear();
                branchPredicted = true;
            }
            speculativeExecution++;
            predictedBranches++;
        }

        decoded.decodeComplete = cycle; // save cycle number of decode stage
        int i = finishedInsts.indexOf(decoded);
        finishedInsts.set(i,decoded);
        if(!unconditionalBranchTaken) {
            decodedQueue.add(decoded);
        }
    }

    private OpType assignOpType(Opcode opcode) {
        switch (opcode) {
            case NOOP:
            case HALT:
                return OpType.OTHER;
            case ADD:
            case ADDI:
            case SUB:
            case MUL:
            case MULI:
            case DIV:
            case DIVI:
            case SHL:
            case SHR:
            case NOT:
            case AND:
            case OR:
            case MOV:
            case MOVC:
            case CMP:
                return OpType.ALU;
            case LD:
            case LDI:
                return OpType.LOAD;
            case ST:
            case STI:
                return OpType.STORE;
            case BR: // unconditional branches
            case JMP:
            case BRZ: // conditional branches
            case BRN:
                return OpType.BRU;
            default:
                return OpType.UNDEFINED;
        }
    }

    private boolean checkSpeculative() {
        for(ReorderBuffer rob : ROB.buffer) {
            if(rob.busy && rob.speculative) {
                return true;
            }
        }
        return false;
    }

    private void Issue() { // issuing decoded instruction to reservation stations

//        int rsIndex = -1;
//        boolean rsBlocked = true;
//        for(int i = 0; i < RS.length; i++) {
//            if(!RS[i].busy) { // there is available rs
//                rsBlocked = false; // RS available
//                rsIndex = i; // get available rs index
//                break;
//            }
//        }
//        boolean robBlocked = ROB.size() >= ROB.capacity; // ROB full
//        issueBlocked = rsBlocked || robBlocked;
//        nothingToIssue = decodedQueue.isEmpty();
//        int size = decodedQueue.size();
//        if(!issueBlocked && !nothingToIssue) {
//            for(int x=0; x < size; x++) {
//                issueLogic(rsIndex);
//            }
//        }
//        if(nothingToIssue) {
//            probes.add(new Probe(cycle,3,0));
//        }
//        else if(rsBlocked) {
//            probes.add(new Probe(cycle,4,decodedQueue.peek().id));
//        }
//        else if(robBlocked) {
//            probes.add(new Probe(cycle,5,decodedQueue.peek().id));
//        }

        int size = decodedQueue.size();
        for(int x=0; x < size; x++) {
            int rsIndex = -1;
            boolean rsBlocked = true;
            for(int i = 0; i < RS.length; i++) {
                if(!RS[i].busy) { // there is available rs
                    rsBlocked = false; // RS available
                    rsIndex = i; // get available rs index
                    break;
                }
            }
            boolean robBlocked = ROB.size() >= ROB.capacity; // ROB full
            issueBlocked = rsBlocked || robBlocked;
            nothingToIssue = decodedQueue.isEmpty();

            if(!issueBlocked && !nothingToIssue) {
                Instruction issuing = decodedQueue.remove();
                ReorderBuffer allocatedROB = new ReorderBuffer();
                // for all ins
                issuing.issueComplete = cycle;
                issuing.rsIndex = rsIndex;
                if(issuing.Rs1 != 0 && regStats[issuing.Rs1].busy) { // there is in-flight ins that writes Rs1
                    int Rs1robIndex = regStats[issuing.Rs1].robIndex;
                    if(ROB.buffer[Rs1robIndex].ready) { // dependent instruction is completed and ready
                        //dependency resolved from ROB
                        RS[rsIndex].V1 = ROB.buffer[Rs1robIndex].value;
                        RS[rsIndex].Q1 = -1;
                    }
                    else {
                        // wait for result from ROB
                        RS[rsIndex].Q1 = Rs1robIndex;
                    }
                }
                else { // no Rs1 dependency
                    RS[rsIndex].V1 = rf[issuing.Rs1]; // 0 if Rs1 = 0
                    RS[rsIndex].Q1 = -1;
                }
                if(issuing.Rs2 != 0 && regStats[issuing.Rs2].busy) { // there is in-flight ins that writes Rs2
                    int Rs2robIndex = regStats[issuing.Rs2].robIndex;
                    if(ROB.buffer[Rs2robIndex].ready) { // dependent instruction is completed and ready
                        //dependency resolved from ROB
                        RS[rsIndex].V2 = ROB.buffer[Rs2robIndex].value;
                        RS[rsIndex].Q2 = -1;
                    }
                    else {
                        // wait for result from ROB
                        RS[rsIndex].Q2 = Rs2robIndex;
                    }
                }
                else { // no Rs2 dependency
                    RS[rsIndex].V2 = rf[issuing.Rs2]; // 0 if Rs2 = 0
                    RS[rsIndex].Q2 = -1;
                }
                // set Reorder Buffer
                allocatedROB.ins = issuing;
                allocatedROB.destination = issuing.Rd;
                allocatedROB.busy = true;
                allocatedROB.ready = false;

                int robIndex = ROB.push(allocatedROB);
                // set Reservation Station
                RS[rsIndex].op = issuing.opcode;
                RS[rsIndex].ins = issuing;
                RS[rsIndex].busy = true;
                RS[rsIndex].robIndex = robIndex;
                RS[rsIndex].type = issuing.opType;

                switch (issuing.opType) {
                    case ALU:
                        // for ins that only use Const
                        if(RS[rsIndex].Q1 == -1 && issuing.opcode.equals(Opcode.MOVC)) {
                            RS[rsIndex].V1 += issuing.Const;
                        }
                        // when second operand is ready
                        else if(RS[rsIndex].Q2 == -1) {

                            RS[rsIndex].V2 += issuing.Const; // for imm instructions
                        }
                        // set regStats
                        if(issuing.Rd != 0) {
                            regStats[issuing.Rd].robIndex = robIndex;
                            regStats[issuing.Rd].busy = true;
                        }
                        break;
                    case LOAD:
                        if(RS[rsIndex].ins.opcode.equals(Opcode.LDI)) {
                            RS[rsIndex].V2 = issuing.Const; // for LDI
                        }
                        // set regStats
                        if(issuing.Rd != 0) {
                            regStats[issuing.Rd].robIndex = robIndex;
                            regStats[issuing.Rd].busy = true;
                        }
                        break;
                    case STORE:
                        if(issuing.Rd != 0 && regStats[issuing.Rd].busy) { // there is in-flight ins that writes at Rd
                            int storeRobIndex = regStats[issuing.Rd].robIndex;
                            if(ROB.buffer[storeRobIndex].ready) { // dependent instruction is completed and ready
                                //dependency resolved from ROB
                                RS[rsIndex].Vs = ROB.buffer[storeRobIndex].value;
                                RS[rsIndex].Qs = -1;
                            }
                            else {
                                // wait for result from ROB
                                RS[rsIndex].Qs = storeRobIndex;
                            }
                        }
                        else { // no Rd dependency
                            RS[rsIndex].Vs = rf[issuing.Rd]; // 0 if Rd = 0
                            RS[rsIndex].Qs = -1;
                        }
                        if(RS[rsIndex].ins.opcode.equals(Opcode.STI)) {
                            RS[rsIndex].V2 = issuing.Const; // for STI
                        }
                        // no regStats set for stores
                        break;
                    case BRU:
                        // for ins that only use Const
                        if(RS[rsIndex].Q1 == -1 && issuing.opcode.equals(Opcode.JMP)) {
                            RS[rsIndex].V1 += issuing.Const;
                        }
                        // when second operand is ready
                        else if(RS[rsIndex].Q2 == -1) {
                            RS[rsIndex].V2 += issuing.Const; // for imm instructions
                        }
                        // no regStats set for branch operations
                        break;
                    case OTHER:
                        break;
                    default:
                        System.out.println("invalid instruction detected at issue stage");
                        finished = true;
                        break;
                }

                int i = finishedInsts.indexOf(issuing);
                finishedInsts.set(i,issuing);
            }
            if(nothingToIssue) {
                probes.add(new Probe(cycle,3,0));
            }
            else if(rsBlocked) {
                probes.add(new Probe(cycle,4,decodedQueue.peek().id));
            }
            else if(robBlocked) {
                probes.add(new Probe(cycle,5,decodedQueue.peek().id));
            }
        }


    }

    private void issueLogic(int rsIndex) {
        Instruction issuing = decodedQueue.remove();
        ReorderBuffer allocatedROB = new ReorderBuffer();
        // for all ins
        issuing.issueComplete = cycle;
        issuing.rsIndex = rsIndex;
        if(issuing.Rs1 != 0 && regStats[issuing.Rs1].busy) { // there is in-flight ins that writes Rs1
            int Rs1robIndex = regStats[issuing.Rs1].robIndex;
            if(ROB.buffer[Rs1robIndex].ready) { // dependent instruction is completed and ready
                //dependency resolved from ROB
                RS[rsIndex].V1 = ROB.buffer[Rs1robIndex].value;
                RS[rsIndex].Q1 = -1;
            }
            else {
                // wait for result from ROB
                RS[rsIndex].Q1 = Rs1robIndex;
            }
        }
        else { // no Rs1 dependency
            RS[rsIndex].V1 = rf[issuing.Rs1]; // 0 if Rs1 = 0
            RS[rsIndex].Q1 = -1;
        }
        if(issuing.Rs2 != 0 && regStats[issuing.Rs2].busy) { // there is in-flight ins that writes Rs2
            int Rs2robIndex = regStats[issuing.Rs2].robIndex;
            if(ROB.buffer[Rs2robIndex].ready) { // dependent instruction is completed and ready
                //dependency resolved from ROB
                RS[rsIndex].V2 = ROB.buffer[Rs2robIndex].value;
                RS[rsIndex].Q2 = -1;
            }
            else {
                // wait for result from ROB
                RS[rsIndex].Q2 = Rs2robIndex;
            }
        }
        else { // no Rs2 dependency
            RS[rsIndex].V2 = rf[issuing.Rs2]; // 0 if Rs2 = 0
            RS[rsIndex].Q2 = -1;
        }
        // set Reorder Buffer
        allocatedROB.ins = issuing;
        allocatedROB.destination = issuing.Rd;
        allocatedROB.busy = true;
        allocatedROB.ready = false;

        int robIndex = ROB.push(allocatedROB);
        // set Reservation Station
        RS[rsIndex].op = issuing.opcode;
        RS[rsIndex].ins = issuing;
        RS[rsIndex].busy = true;
        RS[rsIndex].robIndex = robIndex;
        RS[rsIndex].type = issuing.opType;

        switch (issuing.opType) {
            case ALU:
                // for ins that only use Const
                if(RS[rsIndex].Q1 == -1 && issuing.opcode.equals(Opcode.MOVC)) {
                    RS[rsIndex].V1 += issuing.Const;
                }
                // when second operand is ready
                else if(RS[rsIndex].Q2 == -1) {

                    RS[rsIndex].V2 += issuing.Const; // for imm instructions
                }
                // set regStats
                if(issuing.Rd != 0) {
                    regStats[issuing.Rd].robIndex = robIndex;
                    regStats[issuing.Rd].busy = true;
                }
                break;
            case LOAD:
                if(RS[rsIndex].ins.opcode.equals(Opcode.LDI)) {
                    RS[rsIndex].V2 = issuing.Const; // for LDI
                }
                // set regStats
                if(issuing.Rd != 0) {
                    regStats[issuing.Rd].robIndex = robIndex;
                    regStats[issuing.Rd].busy = true;
                }
                break;
            case STORE:
                if(issuing.Rd != 0 && regStats[issuing.Rd].busy) { // there is in-flight ins that writes at Rd
                    int storeRobIndex = regStats[issuing.Rd].robIndex;
                    if(ROB.buffer[storeRobIndex].ready) { // dependent instruction is completed and ready
                        //dependency resolved from ROB
                        RS[rsIndex].Vs = ROB.buffer[storeRobIndex].value;
                        RS[rsIndex].Qs = -1;
                    }
                    else {
                        // wait for result from ROB
                        RS[rsIndex].Qs = storeRobIndex;
                    }
                }
                else { // no Rd dependency
                    RS[rsIndex].Vs = rf[issuing.Rd]; // 0 if Rd = 0
                    RS[rsIndex].Qs = -1;
                }
                if(RS[rsIndex].ins.opcode.equals(Opcode.STI)) {
                    RS[rsIndex].V2 = issuing.Const; // for STI
                }
                // no regStats set for stores
                break;
            case BRU:
                // for ins that only use Const
                if(RS[rsIndex].Q1 == -1 && issuing.opcode.equals(Opcode.JMP)) {
                    RS[rsIndex].V1 += issuing.Const;
                }
                // when second operand is ready
                else if(RS[rsIndex].Q2 == -1) {
                    RS[rsIndex].V2 += issuing.Const; // for imm instructions
                }
                // no regStats set for branch operations
                break;
            case OTHER:
                break;
            default:
                System.out.println("invalid instruction detected at issue stage");
                finished = true;
                break;
        }

        int i = finishedInsts.indexOf(issuing);
        finishedInsts.set(i,issuing);
    }

    private void Dispatch() {
        rs_aluReady = getReadyRSIndex(OpType.ALU);
        rs_aluReady2 = getReadyRSIndex(OpType.ALU);
        rs_loadReady = getReadyLoadIndex();
        rs_storeReady = getReadyRSIndex(OpType.STORE);
        rs_bruReady = getReadyRSIndex(OpType.BRU);
        rs_otherReady = getReadyOtherIndex();
        if(rs_aluReady > -1) {
            dispatchOperands(rs_aluReady);
        }
        if(rs_aluReady2 > -1) {
            dispatchOperands(rs_aluReady2);
        }
        if(rs_loadReady > -1) {
            dispatchOperands(rs_loadReady);
        }
        if(rs_storeReady > -1) {
            dispatchOperands(rs_storeReady);
        }
        if(rs_bruReady > -1) {
            dispatchOperands(rs_bruReady);
        }
        if(rs_otherReady > -1) {
            // no operand dispatch
            RS[rs_otherReady].ins.dispatchComplete = cycle; // save cycle number of dispatch stage
            Instruction dispatched = RS[rs_otherReady].ins;
            int i = finishedInsts.indexOf(dispatched);
            finishedInsts.set(i,dispatched);
            if(RS[rs_otherReady].op.equals(Opcode.HALT)) {
                beforeFinish = true;
            }
        }
        noReadyInstruction = (rs_aluReady == -1) && (rs_loadReady == -1) && (rs_storeReady == -1) && (rs_bruReady == -1) && (rs_otherReady == -1);
        if(noReadyInstruction) {
            probes.add(new Probe(cycle,6,0));
        }
    }

    private void dispatchOperands(int rs_index) {
        //dispatch operands
        RS[rs_index].ins.data1 = RS[rs_index].V1;
        RS[rs_index].ins.data2 = RS[rs_index].V2;
        RS[rs_index].ins.dispatchComplete = cycle; // save cycle number of dispatch stage
        Instruction dispatched = RS[rs_index].ins;
        int i = finishedInsts.indexOf(dispatched);
        finishedInsts.set(i,dispatched);
    }

    private int getReadyRSIndex(OpType opType) {
        int priority = Integer.MAX_VALUE;
        int readyIndex = -1;
        for(int i=0; i < RS.length; i++) {
            if(
                    RS[i].busy
                    && !RS[i].executing
                    && RS[i].type.equals(opType)
                    && RS[i].Q1 == -1
                    && RS[i].Q2 == -1
                    //&& RS[i].Qs == -1
                    && i != rs_aluReady
                    && i != rs_aluReady2
                    && i != rs_loadReady
                    && i != rs_storeReady
                    && i != rs_bruReady
                    && i != rs_otherReady
            ) {
                // if this was fetched earlier than current priority
                if(RS[i].ins.id < priority) {
                    // this is new ready RS
                    priority = RS[i].ins.id;
                    readyIndex = i;
                }
            }
        }
        return readyIndex;
    }

    private int getReadyLoadIndex() {
        int priority = Integer.MAX_VALUE;
        int readyIndex = -1;
        for(int i = 0; i < RS.length; i++) {
            if(
                    RS[i].busy
                            && !RS[i].executing
                            && RS[i].type.equals(OpType.LOAD)
                            && RS[i].Q1 == -1
                            && RS[i].Q2 == -1
            ) {
                int j = RS[i].robIndex; // j is ROB index of the ins
                if(checkRobForLoadStage1(j) && RS[i].ins.id < priority) {
                    priority = RS[i].ins.id;
                    readyIndex = i;
                }
            }
        }
        return readyIndex;
    }

    private boolean checkRobForLoadStage1(int currentRobIndex) {
        int j = currentRobIndex;
        while (j != ROB.head) {
            if(j == 0) {
                j = ROB.capacity -1;
            }
            else {
                j--;
            }
            if(ROB.buffer[j] != null && ROB.buffer[j].ins.opType.equals(OpType.STORE)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkRobForLoadStage2(int currentRobIndex, int loadAddress) {
        int j = currentRobIndex;
        while (j != ROB.head) {
            if(j == 0) {
                j = ROB.capacity -1;
            }
            else {
                j--;
            }
            if(ROB.buffer[j] != null && ROB.buffer[j].busy && ROB.buffer[j].ins.opType.equals(OpType.STORE) && ROB.buffer[j].address == loadAddress) {
                return false;
            }
        }
        return true;
    }

    private int getReadyBranchIndex() {
        int priority = Integer.MAX_VALUE;
        int readyIndex = -1;
        for(int i=0; i < RS.length; i++) {
            if(
                    RS[i].busy
                    && !RS[i].executing
                    && RS[i].type.equals(OpType.BRU)
                    && RS[i].Q1 == -1
                    && RS[i].Q2 == -1
                    && RS[i].Qs == -1
            ) {
                // if this was fetched earlier than current priority
                if(RS[i].ins.id < priority) {
                    // this is new ready RS
                    priority = RS[i].ins.id;
                    readyIndex = i;
                }
            }
        }
        return readyIndex;
    }

    private int getReadyOtherIndex() {
        int priority = Integer.MAX_VALUE;
        int readyIndex = -1;
        for(int i=0; i < RS.length; i++) {
            if(
                    RS[i].busy
                    && !RS[i].executing
                    && RS[i].type.equals(OpType.OTHER)
                    && RS[i].Q1 == -1
                    && RS[i].Q2 == -1
                    && RS[i].Qs == -1
            ) {
                if(RS[i].ins.opcode.equals(Opcode.HALT) && !ROB.peek().ins.equals(RS[i].ins)) {
                    continue; // when it's HALT but if it's not the head of ROB, don't dispatch it
                }
                // if this was fetched earlier than current priority
                if(RS[i].ins.id < priority) {
                    // this is new ready RS
                    priority = RS[i].ins.id;
                    readyIndex = i;
                }
            }
        }
        return readyIndex;
    }

    private void Execute() {
        loadBufferFull = loadBuffer.size() >= QUEUE_SIZE;
        writeBackBufferFull = beforeWriteBack.size() >= QUEUE_SIZE;
        executeBlocked = (writeBackBufferFull && (rs_aluReady > -1 || rs_storeReady > -1 || rs_otherReady > -1)) || (loadBufferFull && rs_loadReady > -1);
        nothingToExecute = rs_aluReady == -1 && rs_loadReady == -1 && rs_storeReady == -1 && rs_bruReady == -1 && rs_otherReady == -1;
        euAllBusy = (alu0.busy && alu1.busy);
        boolean loadAddressReady = false;
        boolean storeAddressReady = false;
        if(!executeBlocked && !nothingToExecute) {
            if(rs_bruReady > -1) {
                // verify branch
                Instruction executing = RS[rs_bruReady].ins;
                RS[rs_bruReady].executing = true;
                executing.executeComplete = cycle;
                boolean realBranchCondition = bru0.evaluateCondition(executing.opcode, executing.data1);
                if(realBranchCondition && executing.predicted && executing.taken) {
                    // well predicted taken branch
                    correctPrediction++;
                    BTBstatus oldBtbCondition = BTB.get(executing.insAddress);
                    switch (oldBtbCondition) {
                        case STRONG_YES:
                        case YES:
                            BTB.put(executing.insAddress,BTBstatus.STRONG_YES);
                            break;
                        default:
                            System.out.println("illegal btb status detected at execute stage");
                            break;
                    }
                }
                else if(!realBranchCondition && executing.predicted && !executing.taken) {
                    // well predicted denied branch
                    correctPrediction++;
                    BTBstatus oldBtbCondition = BTB.get(executing.insAddress);
                    switch (oldBtbCondition) {
                        case STRONG_NO:
                        case NO:
                            BTB.put(executing.insAddress,BTBstatus.STRONG_NO);
                            break;
                        default:
                            System.out.println("illegal btb status detected at execute stage");
                            break;
                    }
                }
                else {
                    // prediction failed
                    misprediction++;
                    ROB.buffer[RS[executing.rsIndex].robIndex].mispredicted = true;
                    probes.add(new Probe(cycle,15,executing.id));
                }
                ROB.buffer[RS[executing.rsIndex].robIndex].ready = true;
                RS[rs_bruReady] = new ReservationStation();
                int i = finishedInsts.indexOf(executing);
                finishedInsts.set(i,executing);
            }
            if(rs_aluReady > -1) {
                Instruction executing = RS[rs_aluReady].ins;
                if(!alu0.busy) {
                    alu0.update(executing.opcode,executing.data1,executing.data2);
                    alu0.executing = executing;
                    executing.executeComplete = cycle;
                    RS[executing.rsIndex].executing = true;
                }
//                else if(!alu1.busy) {
//                    alu1.update(executing.opcode, executing.data1, executing.data2);
//                    alu1.executing = executing;
//                    executing.executeComplete = cycle;
//                    RS[executing.rsIndex].executing = true;
//                }
                int i = finishedInsts.indexOf(executing);
                finishedInsts.set(i,executing);
            }
            if(rs_aluReady2 > -1) {
                Instruction executing = RS[rs_aluReady2].ins;
                if(!alu1.busy) {
                    alu1.update(executing.opcode, executing.data1, executing.data2);
                    alu1.executing = executing;
                    executing.executeComplete = cycle;
                    RS[executing.rsIndex].executing = true;
                }
                int i = finishedInsts.indexOf(executing);
                finishedInsts.set(i,executing);
            }
            if(rs_loadReady > -1 && !loadBufferFull) {
                Instruction executing = RS[rs_loadReady].ins;
                RS[rs_loadReady].executing = true;
                executing.memAddress = agu.evaluate(Opcode.ADD,executing.data1,executing.data2);
                executing.executeComplete = cycle;
                RS[rs_loadReady].ins = executing;
                loadAddressReady = true;
                int i = finishedInsts.indexOf(executing);
                finishedInsts.set(i,executing);
            }
            if(rs_storeReady > -1) {
                Instruction executing = RS[rs_storeReady].ins;
                int memAddress = agu.evaluate(Opcode.ADD,executing.data1,executing.data2);
                executing.memAddress = memAddress;
                executing.executeComplete = cycle;
                RS[rs_storeReady].A = memAddress;
                ROB.buffer[RS[rs_storeReady].robIndex].address = memAddress;

                if(RS[rs_storeReady].Qs == -1) {
                    RS[rs_storeReady].executing = true;
                    storeAddressReady = true;
                    executedInsts++;
                    int i = finishedInsts.indexOf(executing);
                    finishedInsts.set(i,executing);
                    beforeWriteBack.add(RS[rs_storeReady].ins);
                    executedInsts++;
                }
                else {
                    rs_storeReady = -1;
                }
            }
            if(rs_otherReady > -1) {
                Instruction executing = RS[rs_otherReady].ins;
                RS[rs_otherReady].executing = true;
                executing.executeComplete = cycle;
                int i = finishedInsts.indexOf(executing);
                finishedInsts.set(i,executing);
                executedInsts++;
                beforeWriteBack.add(executing);
                // do nothing here
            }
        }
        Instruction alu0_result = alu0.execute();
        Instruction alu1_result = alu1.execute();
        if(alu0_result != null && alu0_result.result != null) {
            beforeWriteBack.add(alu0_result);
            resultForwardingFromRS(alu0_result);
            alu0.reset();
            executedInsts++;
        }
        if(alu1_result != null && alu1_result.result != null) {
            beforeWriteBack.add(alu1_result);
            resultForwardingFromRS(alu1_result);
            alu1.reset();
            executedInsts++;
        }
        if(loadAddressReady) {
            loadBuffer.add(RS[rs_loadReady].ins);
            executedInsts++;
        }
//        if(storeAddressReady) {
//            beforeWriteBack.add(RS[rs_storeReady].ins);
//            executedInsts++;
//        }

        if(nothingToExecute) {
            probes.add(new Probe(cycle,7,0));
        }
        if(executeBlocked) {
            probes.add(new Probe(cycle,8,0));
        }
        if(euAllBusy) {
            probes.add(new Probe(cycle,9,0));
        }
    }

    private void Memory() {
        if(loadBuffer.isEmpty()) {
            probes.add(new Probe(cycle,10,0));
            return;
        }
        while(!loadBuffer.isEmpty()) {
            Instruction loading = loadBuffer.peek();
            if(!loading.opType.equals(OpType.LOAD)) {
                System.out.println("illegal instruction detected at memory stage");
                finished = true;
                return;
            }
            if(!checkRobForLoadStage2(RS[loading.rsIndex].robIndex, loading.memAddress)) {
                probes.add(new Probe(cycle,11,loading.id));
                return;
            }
            loading.result = mem[loading.memAddress];
            resultForwardingFromRS(loading);
            loadBuffer.remove();

            loading.memoryComplete = cycle; // save cycle number of memory stage
            int i = finishedInsts.indexOf(loading);
            finishedInsts.set(i,loading);
            beforeWriteBack.add(loading);
        }
    }

    private void WriteBack() {
        Queue<Instruction> copy = new LinkedList<>();
        while(!beforeWriteBack.isEmpty()) {
            Instruction writeBack = beforeWriteBack.remove();

            if(!writeBack.opType.equals(OpType.LOAD)) {
                writeBack.memoryComplete = cycle;
            }

            int rsIndex = writeBack.rsIndex;
            int robIndex = RS[rsIndex].robIndex;

            switch (writeBack.opType) {
                case STORE:
                    if(robIndex == ROB.head) {
                        ROB.buffer[ROB.head].value = RS[rsIndex].Vs;
                        writeBack.writeBackComplete = cycle;
                        ROB.buffer[robIndex].ready = true;
                        resultForwardingFromRS(writeBack);
                        RS[rsIndex] = new ReservationStation(); // clear RS entry
                    }
                    else {
                        probes.add(new Probe(cycle,12, writeBack.id));
                        copy.add(writeBack);
                    }
                    break;
                case BRU:
                case OTHER:
                    ROB.buffer[robIndex].ready = true;
                    writeBack.writeBackComplete = cycle;
                    resultForwardingFromRS(writeBack);
                    RS[rsIndex] = new ReservationStation(); // clear RS entry
                    break;
                case ALU:
                case LOAD:
                    if(writeBack.Rd != 0) {
                        ROB.buffer[robIndex].value = writeBack.result;
                    }
                    ROB.buffer[robIndex].ready = true;
                    writeBack.writeBackComplete = cycle;
                    resultForwardingFromRS(writeBack);
                    RS[rsIndex] = new ReservationStation(); // clear RS entry
                    break;
                default:
                    System.out.println("illegal optype detected at WriteBack stage");
                    finished = true;
                    break;
            }
            int i = finishedInsts.indexOf(writeBack);
            finishedInsts.set(i,writeBack);
        }
        beforeWriteBack.addAll(copy);
    }

    private void resultForwardingFromRS(Instruction forwarding) {
        int b = RS[forwarding.rsIndex].robIndex;
        if(b == -1) {
            return;
        }
        for(ReservationStation rs : RS) {
            if(rs.Q1 == b) {
                rs.V1 = forwarding.result;
                rs.Q1 = -1;
            }
            if(rs.Q2 == b) {
                rs.V2 = forwarding.result;
                rs.Q2 = -1;
            }
            if(rs.Qs == b) {
                rs.Vs = forwarding.result;
                rs.Qs = -1;
            }
        }
    }

    private void resultForwardingFromROB(int robIndex, int value) {
        for(ReservationStation rs : RS) {
            if(rs.Q1 == robIndex) {
                rs.V1 = value;
                rs.Q1 = -1;
            }
            if(rs.Q2 == robIndex) {
                rs.V2 = value;
                rs.Q2 = -1;
            }
            if(rs.Qs == robIndex) {
                rs.Vs = value;
                rs.Qs = -1;
            }
        }
    }

    private void Commit() {
        while(!ROB.isEmpty()) {
            int h = ROB.head;
            ReorderBuffer robHead = ROB.peek();
            if(!robHead.ready) { // head is not ready to commit
                probes.add(new Probe(cycle,13,robHead.ins.id));
                break; // abort committing
            }
            if(robHead.ins.opType.equals(OpType.BRU)) {
                if(robHead.mispredicted) {
                    // flip BTB condition
                    BTBstatus oldBtbCondition = BTB.get(robHead.ins.insAddress);
                    BTBstatus newBtbCondition = oldBtbCondition;
                    switch (oldBtbCondition) {
                        case STRONG_YES:
                        case NO:
                            newBtbCondition = BTBstatus.YES;
                            break;
                        case YES:
                        case STRONG_NO:
                            newBtbCondition = BTBstatus.NO;
                            break;
                    }
                    BTB.put(robHead.ins.insAddress,newBtbCondition);
                    // clear reorder buffer
                    ROB.clear();
                    // clear register status
                    for(int i = 0; i < regStats.length; i++) {
                        regStats[i] = new RegisterStatus();
                    }
                    // clear reservation station entries that is later than this branch
                    for(int i = 0; i < RS.length; i++) {
                        RS[i] = new ReservationStation();
                    }
                    rs_aluReady = -1;
                    rs_loadReady = -1;
                    rs_storeReady = -1;
                    rs_bruReady = -1;
                    rs_otherReady = -1;
                    // flush queues
                    fetchedQueue.clear();
                    decodedQueue.clear();
                    loadBuffer.clear();
                    beforeWriteBack.clear();
                    // change to correct pc
                    if(robHead.ins.taken) {
                        pc = robHead.ins.insAddress + 1;
                    }
                    else {
                        pc = robHead.ins.Const;
                    }
                    probes.add(new Probe(cycle,16,robHead.ins.id));
                }
                speculativeExecution--;
            }

            else if(robHead.ins.opType.equals(OpType.STORE)) {
                if(robHead.address >= mem.length) {
                    finished = true;
                    System.out.println("memory index out of range at commit");
                    return;
                }
                mem[robHead.address] = robHead.value; // update memory here
//                System.out.println("Cycle: " + cycle + " PC: "  + pc + " id: " + robHead.ins.id);
//                System.out.print("[");
//                for (int j : mem) {
//                    System.out.printf("%d, ", j);
//                }
//                System.out.print("]\n");
            }
            else if(robHead.ins.opcode.equals(Opcode.HALT)) {
                finished = true;
            }
            else { // update registers with result
                int Rd = robHead.destination;
                rf[Rd] = robHead.value;
                resultForwardingFromROB(h,robHead.value);
                if(regStats[Rd].busy && regStats[Rd].robIndex == h) {
                    regStats[Rd] = new RegisterStatus(); // free up register status entry
                }
            }
            int i = finishedInsts.indexOf(robHead.ins);
            Instruction committing = finishedInsts.get(i);
            committing.commitComplete = cycle;
            finishedInsts.set(i,committing);

            ROB.buffer[ROB.head].busy = false;
            ROB.pop(); // free up ROB entry, new head
        }
    }

    public void RunProcessor() {
        for(int i=0; i < RS.length; i++) {
            RS[i] = new ReservationStation();
        }
        for(int i=0; i < regStats.length; i++) {
            regStats[i] = new RegisterStatus();
        }
        int cycleLimit = 10000;
        while(!finished && pc < instructions.length && cycle < cycleLimit) {
            Commit();
            WriteBack();
            Memory();
            Execute();
            Dispatch();
            Issue();
            Decode();
            Fetch();
            cycle++;
            if(!beforeFinish) {
                if(fetchBlocked || decodeBlocked || issueBlocked || executeBlocked || euAllBusy || loadBufferFull) {
                    stalledCycle++;
                }
                else if(nothingToDecode || nothingToIssue || noReadyInstruction || nothingToExecute || nothingToMemory || nothingToWriteBack) {
                    waitingCycle++;
                }
            }
//            if(issueBlocked) {
//                System.out.printf("Issue blocked at PC: %d\nRS entries\n",pc);
//                for(ReservationStation rs : RS) {
//                    if(rs.busy) {
//                        System.out.printf("%d:%s:%d ",rs.ins.id,rs.op.toString(),rs.ins.insAddress);
//                    }
//                }
//                System.out.println();
//            }
            //System.out.println("Cycle: " + cycle + " PC: " + pc);
        }
        finishedInsts.sort(Comparator.comparingInt((Instruction i) -> i.id));
        TraceEncoder traceEncoder = new TraceEncoder(finishedInsts);
        ProbeEncoder probeEncoder = new ProbeEncoder(probes,cycle);
        try {
            traceEncoder.createTrace("../ACA-tracer/trace.out");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            probeEncoder.createProbe("../ACA-tracer/probe.out");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(cycle >= cycleLimit) {
            System.out.println("Time out");
        }
        System.out.println(superScalarWidth + "-way Superscalar Out of Order 8-stage pipeline processor Terminated");
        System.out.println(executedInsts + " instructions executed");
        System.out.println(cycle + " cycles spent");
        System.out.println(stalledCycle + " stalled cycles");
        System.out.println(waitingCycle + " Waiting cycles");
        System.out.println(predictedBranches + " branches predicted");
        System.out.println(correctPrediction + " correct predictions");
        System.out.println(misprediction + " incorrect predictions");
        System.out.println("cycles/instruction ratio: " + ((float) cycle) / (float) executedInsts);
        System.out.println("Instructions/cycle ratio: " + ((float) executedInsts / (float) cycle));
        System.out.println("stalled_cycle/cycle ratio: " + ((float) stalledCycle / (float) cycle));
        System.out.println("wasted_cycle/cycle ratio: " + ((float) (stalledCycle + waitingCycle) / (float) cycle));
        System.out.println("correct prediction rate: "+ ((float) correctPrediction / (float) (correctPrediction + misprediction)));
    }
}