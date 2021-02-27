public class Main {

    public static void main(String[] args) {
        Instruction[] instructions = new Instruction[512];
        int[] mem = new int[1024];
        //Functionality testing program

        mem[0] = 5;
        mem[1] = 7;
        instructions[0] = new Instruction(Opcode.LDC,0,0,0,1000);
        instructions[1] = new Instruction(Opcode.LDI,1,0,0,0);
        instructions[2] = new Instruction(Opcode.LDI,2,0,0,1);
        instructions[3] = new Instruction(Opcode.ADD, 3,1,2,0);
        instructions[4] = new Instruction(Opcode.ADDI,4,3,0,10);
        instructions[5] = new Instruction(Opcode.ST,4,1,0,0);
        instructions[6] = new Instruction(Opcode.MUL,5,1,2,0);
        instructions[7] = new Instruction(Opcode.DIV, 6, 3,1,0);
        instructions[8] = new Instruction(Opcode.AND,7,1,2,0);
        instructions[9] = new Instruction(Opcode.OR,8,1,2,0);
        instructions[10] = new Instruction(Opcode.MV,9,7,0,0);
        instructions[11] = new Instruction(Opcode.BEQ, 0,1,2,509);
        instructions[12] = new Instruction(Opcode.BEQ, 0,1,1,14);
        instructions[14] = new Instruction(Opcode.BLT,0,2,1,508);
        instructions[15] = new Instruction(Opcode.BLT,0,1,2,18);
        instructions[18] = new Instruction(Opcode.LDC,10,0,0,1000);
        instructions[19] = new Instruction(Opcode.LD,11,0,1,0);
        instructions[20] = new Instruction(Opcode.CMP,12,1,0,0);
        instructions[21] = new Instruction(Opcode.CMP,12,0,0,0);
        instructions[22] = new Instruction(Opcode.CMP,12,0,1,0);
        instructions[23] = new Instruction(Opcode.STI,1,0,0,3);
        instructions[24] = new Instruction(Opcode.JMP,0,0,0,6);
        instructions[30] = new Instruction(Opcode.MULI,1,1,0,2);
        instructions[31] = new Instruction(Opcode.DIVI,1,4,0,6);
        instructions[32] = new Instruction(Opcode.NOT,1,2,0,0);
        instructions[33] = new Instruction(Opcode.BR,0,0,0,500);
        instructions[500] = new Instruction(Opcode.HALT,0,0,0,0);


        //Vector addition
        /*
        int length = 5;
        int ap = 2;
        int bp = 10;
        int cp = 16;
        for(int i = 0; i < length; i++) {
            mem[ap + i] = i; //assigning A[i]
            mem[bp + i] = 2 * i; //assigning B[i]
        }
        instructions[0] = new Instruction(Opcode.LDC,1,0,0,ap); //pointer to array A
        instructions[1] = new Instruction(Opcode.LDC,2,0,0,bp); //pointer to array B
        instructions[2] = new Instruction(Opcode.LDC,3,0,0,cp); //pointer to array C
        instructions[3] = new Instruction(Opcode.LDC,4,0,0,0); // i = 0
        instructions[4] = new Instruction(Opcode.LDC,8,0,0,length); // for loop limit
        instructions[5] = new Instruction(Opcode.LD,5,1,4,0); // a = A[&A + i], for loop starts
        instructions[6] = new Instruction(Opcode.LD,6,2,4,0); // b = B[&B + i]
        instructions[7] = new Instruction(Opcode.ADD,7,5,6,0); // c = a + b
        instructions[8] = new Instruction(Opcode.ST,7,3,4,0); // C[&C + i] = c
        instructions[9] = new Instruction(Opcode.ADDI,4,4,0,1); // i++
        instructions[10] = new Instruction(Opcode.BLT,0,4,8,5); //branch back to for loop if i < 100
        instructions[11] = new Instruction(Opcode.HALT,0,0,0,0); //Terminate

         */

        //Bubble sort
        Instruction[] instructions2 = new Instruction[512];
        int[] mem2 = new int[1024];

        int[] arrayToSort = {512,52,61,3,-6,-127,75,21,98,1,874,-1239,431,94,10,36};
        //int[] arrayToSort = {5,4,3,2,1};
        int pointer = 2;
        System.arraycopy(arrayToSort,0,mem2,pointer,arrayToSort.length);
        instructions2[0] = new Instruction(Opcode.LDC,1,0,0,pointer); // load array pointer
        instructions2[1] = new Instruction(Opcode.LDC,2,0,0,0); // i = 0
        instructions2[2] = new Instruction(Opcode.LDC,4,0,0, arrayToSort.length); // load array length
        instructions2[3] = new Instruction(Opcode.ADDI,5,4,0,-1); // outer for loop limit = length - 1
        instructions2[4] = new Instruction(Opcode.SUB,6,4,2,0); // length - i, outer loop starting point
        instructions2[5] = new Instruction(Opcode.LDC,3,0,0,0); // j = 0
        instructions2[6] = new Instruction(Opcode.ADDI,6,6,0,-1); //inner for loop limit = length - i - 1
        instructions2[7] = new Instruction(Opcode.LD,7,1,3,0); // a = array[j], inner loop starting point
        instructions2[8] = new Instruction(Opcode.ADD,9,1,3,0); // pointer + j
        instructions2[9] = new Instruction(Opcode.LDO,8,9,0,1); // b = array[j + 1]
        instructions2[10] = new Instruction(Opcode.BLT,0,8,7,12); // if(b < a)
        instructions2[11] = new Instruction(Opcode.BR,0,0,0,17); //if not b < a skip to the end of inner loop
        instructions2[12] = new Instruction(Opcode.MV,10,7,0,0); // temp = a
        instructions2[13] = new Instruction(Opcode.MV,7,8,0,0); // a = b
        instructions2[14] = new Instruction(Opcode.MV,8,10,0,0); // b = temp, swap complete
        instructions2[15] = new Instruction(Opcode.ST,7,1,3,0); // store array[j] = a
        instructions2[16] = new Instruction(Opcode.STO,8,9,0,1); // store array[j + 1] = b
        instructions2[17] = new Instruction(Opcode.ADDI,3,3,0,1); // j++
        instructions2[18] = new Instruction(Opcode.BLT,0,3,6,7); // loop back to inner starting point if j < length - i - 1
        instructions2[19] = new Instruction(Opcode.ADDI,2,2,0,1); // i++
        instructions2[20] = new Instruction(Opcode.BLT,0,2,5,4); // loop back to outer starting point if i < length - 1
        instructions2[21] = new Instruction(Opcode.HALT,0,0,0,0); // halt

        // factorial (recursion)
        Instruction[] instructions3 = new Instruction[512];
        int[] mem3 = new int[1024];
        int loc = 3;
        int num = 5;
        int sp = 100;
        mem3[loc] = num;

        instructions3[0] = new Instruction(Opcode.LDI,1,0,0,loc); // load argument
        instructions3[1] = new Instruction(Opcode.MV,2,1,0,0); // copy argument
        instructions3[2] = new Instruction(Opcode.LDC,29,0,0,sp); // gpr[29] is stack pointer
        instructions3[3] = new Instruction(Opcode.LDC,31,0,0,5); // gpr[31] is return address
        instructions3[4] = new Instruction(Opcode.BR,0,0,0,100); // function call
        instructions3[5] = new Instruction(Opcode.HALT,0,0,0,0); // return address,
        //factorial function
        instructions3[100] = new Instruction(Opcode.BEQ,0,2,0,102); // function starting point, branch to base case
        instructions3[101] = new Instruction(Opcode.BR,0,0,0,200); // recursive case
        instructions3[102] = new Instruction(Opcode.LDC,5,0,0,1); // base case, return value 1
        instructions3[103] = new Instruction(Opcode.JR,0,31,0,0); // return back
        //recursive case
        instructions3[200] = new Instruction(Opcode.ADDI,29,29,0,-2); // need to store num and ra, sp = sp - 2
        instructions3[201] = new Instruction(Opcode.STO,31,29,0,0); // store ra to stack
        instructions3[202] = new Instruction(Opcode.STO,2,29,0,-1); // store num to stack
        instructions3[203] = new Instruction(Opcode.ADDI,2,2,0,-1); // num = num - 1
        instructions3[204] = new Instruction(Opcode.LDC,31,0,0,206); // new return address
        instructions3[205] = new Instruction(Opcode.BR,0,0,0,100); // call factorial



        Processor processor = new Processor();
        processor.instructions = instructions2;
        processor.mem = mem2;
	    processor.RunProcessor();

	    Processor3 processor3 = new Processor3();
	    processor3.instructions = instructions;
	    processor3.mem = mem;
	    processor3.RunProcessor();
    }
}
