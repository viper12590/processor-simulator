public class Processor {

    int cycle = 0;
    int pc = 0; //Program counter
    int executedInsts = 0; //Number of instructions executed
    int[] mem;
    int[] rf = new int[64]; //Register file (physical) register 0 always have value zero (input is ignored)
    Instruction[] instructions;
    boolean finished = false;

    public Processor() {

    }
    //This will fetch int instead later
    public Instruction Fetch() {
        Instruction instruction = instructions[pc];
        cycle++;
        return instruction;
    }

    public Instruction Decode(Instruction ins) {
        if(ins == null) {
            ins = new Instruction();
            ins.opcode = Opcode.NOOP;
        }
        cycle++;
        return ins;
    }

    public void Execute(Instruction ins) {
        switch (ins.opcode) {
            case NOOP:
                cycle++;
                pc++;
                break;
            case ADD:
                rf[ins.Rd] = rf[ins.Rs1] + rf[ins.Rs2];
                cycle += 2;
                pc++;
                break;
            case ADDI:
                rf[ins.Rd] = rf[ins.Rs1] + ins.Const;
                cycle += 2;
                pc++;
                break;
            case SUB:
                rf[ins.Rd] = rf[ins.Rs1] - rf[ins.Rs2];
                cycle += 2;
                pc++;
                break;
            case MUL:
                rf[ins.Rd] = rf[ins.Rs1] * rf[ins.Rs2];
                cycle += 3;
                pc++;
                break;
            case MULI:
                rf[ins.Rd] = rf[ins.Rs1] * ins.Const;
                cycle += 3;
                pc++;
                break;
            case DIV:
                rf[ins.Rd] = rf[ins.Rs1] / rf[ins.Rs2];
                cycle += 4;
                pc++;
                break;
            case DIVI:
                rf[ins.Rd] = rf[ins.Rs1] / ins.Const;
                cycle += 4;
                pc++;
                break;
            case NOT:
                rf[ins.Rd] = ~rf[ins.Rs1];
                cycle++;
                pc++;
                break;
            case AND:
                rf[ins.Rd] = rf[ins.Rs1] & rf[ins.Rs2];
                cycle++;
                pc++;
                break;
            case OR:
                rf[ins.Rd] = rf[ins.Rs1] | rf[ins.Rs2];
                cycle++;
                pc++;
                break;
            case LD:
                if(ins.Rd != 0) {
                    rf[ins.Rd] = mem[rf[ins.Rs1] + rf[ins.Rs2]];
                }
                cycle++;
                pc++;
                break;
            case LDC:
                if(ins.Rd != 0) {
                    rf[ins.Rd] = ins.Const;
                }
                cycle++;
                pc++;
                break;
            case LDI:
                if(ins.Rd != 0) {
                    rf[ins.Rd] = mem[ins.Const];
                }
                cycle++;
                pc++;
                break;
            case LDO:
                if(ins.Rd != 0) {
                    rf[ins.Rd] = mem[rf[ins.Rs1] + ins.Const];
                }
                cycle++;
                pc++;
                break;
            case ST:
                mem[rf[ins.Rs1] + rf[ins.Rs2]] = rf[ins.Rd];
                cycle++;
                pc++;
                break;
            case STI:
                mem[ins.Const] = rf[ins.Rd];
                cycle++;
                pc++;
                break;
            case STO:
                mem[rf[ins.Rs1] + ins.Const] = rf[ins.Rd];
                cycle++;
                pc++;
                break;
            case MV:
                rf[ins.Rd] = rf[ins.Rs1];
                cycle++;
                pc++;
                break;
            case BR:
                pc = ins.Const;
                cycle++;
                break;
            case JMP:
                pc = pc + ins.Const;
                cycle++;
                break;
            case JR:
                pc = ins.Rs1;
                cycle++;
                break;
            case BEQ:
                if(rf[ins.Rs1] == rf[ins.Rs2]) {
                    pc = ins.Const;
                }
                else {
                    pc++;
                }
                cycle++;
                break;
            case BLT:
                if(rf[ins.Rs1] < rf[ins.Rs2]) {
                    pc = ins.Const;
                }
                else {
                    pc++;
                }
                cycle++;
                break;
            case CMP:
                rf[ins.Rd] = Integer.compare(rf[ins.Rs1], rf[ins.Rs2]);
                cycle++;
                pc++;
                break;
            case HALT:
                finished = true;
                cycle++;
                break;
            default:
                cycle++;
                break;
        }

    }

    public void RunProcessor() {

        while(!finished && pc < instructions.length) {
            //System.out.println("PC " + pc + " " + cycle + " number of cycles passed");
            Instruction fetched = Fetch();
            Instruction instruction = Decode(fetched);
            Execute(instruction);
            executedInsts++;
        }
        System.out.println("3 cycle scalar non-pipelined processor Terminated");
        System.out.println(executedInsts + " instructions executed");
        System.out.println(cycle + " cycles spent");
        System.out.println("Instructions/cycle ratio: " + ((float) executedInsts / (float) cycle));
    }

}
