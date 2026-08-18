package com.diniz.xscdecompiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Clean-room GTA V Xbox 360 XSC parser/decompiler.
 *
 * Output is intentionally C-like reconstructed source. Compilation destroys
 * original local names, comments, typedefs and some high-level control flow,
 * so those are represented with stable generated names and labels.
 */
public final class XscDecompiler {
    private static final int PAGE = 1 << 14;

    public static final class Result {
        public final String scriptName;
        public final String source;
        public final int codeLength, nativesCount, staticsCount, globalsCount;
        Result(String n,String s,int c,int na,int st,int gl){
            scriptName=n;source=s;codeLength=c;nativesCount=na;staticsCount=st;globalsCount=gl;
        }
    }

    private static final String[] OPS = new String[255];
    static {
        String[] known = {
            "Nop","Add","Sub","Mult","Div","Mod","Not","Neg","CmpEQ","CmpNE","CmpGT","CmpGE","CmpLT","CmpLE",
            "fAdd","fSub","fMult","fDiv","fMod","fNeg","fCmpEQ","fCmpNE","fCmpGT","fCmpGE","fCmpLT","fCmpLE",
            "vAdd","vSub","vMult","vDiv","vNeg","And","Or","Xor","ItoF","FtoI","FtoV",
            "PushB","PushB2","PushB3","Push","PushF","Dup","Drop","CallNative","Function","Return",
            "pGet","pSet","pPeekSet","ToStack","FromStack",
            "GetArrayP1","GetArray1","SetArray1","GetLocalP1","GetLocal1","SetLocal1","GetStaticP1","GetStatic1","SetStatic1",
            "AddImm1","MultImm1","GetImmPs","GetImmP1","GetImm1","SetImm1","PushS",
            "AddImm2","MultImm2","GetImmP2","GetImm2","SetImm2","GetArrayP2","GetArray2","SetArray2",
            "GetLocalP2","GetLocal2","SetLocal2","GetStaticP2","GetStatic2","SetStatic2","GetGlobalP2","GetGlobal2","SetGlobal2",
            "Jump","JumpFalse","JumpNE","JumpEQ","JumpLE","JumpLT","JumpGE","JumpGT","Call",
            "GetGlobalP3","GetGlobal3","SetGlobal3","PushI24","Switch","PushStringS","GetHash","StrCopy","ItoS","StrAdd","StrAddi",
            "MemCopy","Catch","Throw","pCall",
            "Push_Neg1","Push_0","Push_1","Push_2","Push_3","Push_4","Push_5","Push_6","Push_7",
            "PushF_Neg1","PushF_0","PushF_1","PushF_2","PushF_3","PushF_4","PushF_5","PushF_6","PushF_7"
        };
        System.arraycopy(known,0,OPS,0,known.length);
        for(int i=known.length;i<OPS.length;i++) OPS[i]="Op_"+hex2(i);
    }

    public Result decompile(byte[] file) throws IOException {
        if(file==null || file.length<80) throw new IOException("Arquivo XSC pequeno demais.");

        int base=0;
        if(file.length>=16 && file[0]=='R'&&file[1]=='S'&&file[2]=='C'&&file[3]=='7') base=16;
        if(base+80>file.length) throw new IOException("Cabeçalho XSC incompleto.");

        int signature=be32(file,base+12);
        int codeLen=positive(be32(file,base+16),"codeLength");
        int paramCount=positive(be32(file,base+20),"parameterCount");
        int staticsCount=positive(be32(file,base+24),"staticsCount");
        int globalsCount=positive(be32(file,base+28),"globalsCount");
        int nativesCount=positive(be32(file,base+32),"nativesCount");
        int staticsRel=rel(be32(file,base+36));
        int globalsRel=rel(be32(file,base+40));
        int nativesRel=rel(be32(file,base+44));
        int scriptNameRel=rel(be32(file,base+64));
        int codeBlocksRel=rel(be32(file,base+8));
        int stringsRel=rel(be32(file,base+68));
        int totalStrings=positive(be32(file,base+72),"stringLength");

        String scriptName=safeCString(file,base+scriptNameRel,file.length);
        if(scriptName.isEmpty()) scriptName="script_"+Integer.toHexString(be32(file,base+56));

        byte[] code=loadPaged(file,base,codeBlocksRel,codeLen,"código");
        byte[] strings= totalStrings==0 ? new byte[0] : loadPaged(file,base,stringsRel,totalStrings,"strings");

        long[] natives=new long[nativesCount];
        for(int i=0;i<nativesCount;i++){
            int p=base+nativesRel+i*4;
            if(p<base || p+4>file.length) throw new IOException("Tabela de natives fora do arquivo.");
            natives[i]=u32be(file,p);
        }

        long[] statics=readWords(file,base+staticsRel,staticsCount);
        long[] globals=readWords(file,base+globalsRel,globalsCount);

        Decoder decoder=new Decoder(code,strings,natives);
        String body=decoder.render(scriptName);

        StringBuilder out=new StringBuilder(body.length()+4096);
        out.append("/*\n")
           .append(" * XSC Decompiler 360 - fonte C reconstruído\n")
           .append(" * Script: ").append(sanitizeComment(scriptName)).append("\n")
           .append(" * Signature: 0x").append(hex8(signature)).append("\n")
           .append(" * Code bytes: ").append(codeLen).append(" | params: ").append(paramCount)
           .append(" | statics: ").append(staticsCount).append(" | globals: ").append(globalsCount)
           .append(" | natives: ").append(nativesCount).append("\n")
           .append(" *\n")
           .append(" * IMPORTANTE: nomes originais de variáveis, comentários, typedefs e parte da\n")
           .append(" * estrutura de controle não existem mais no bytecode. O arquivo abaixo usa\n")
           .append(" * nomes determinísticos e labels para não inventar informação perdida.\n")
           .append(" */\n\n")
           .append("#include <stdint.h>\n\n")
           .append("typedef int32_t scr_int;\n")
           .append("typedef float scr_float;\n")
           .append("typedef uint32_t scr_ptr;\n\n");

        if(statics.length>0){
            out.append("static scr_int STATIC_DATA[").append(statics.length).append("] = {");
            appendData(out,statics);
            out.append("};\n");
        }
        if(globals.length>0){
            out.append("/* Global initial data present in the resource. */\n");
            out.append("static scr_int GLOBAL_INIT[").append(globals.length).append("] = {");
            appendData(out,globals);
            out.append("};\n");
        }
        out.append("\n/* Native hashes/index values from the XSC table. */\n");
        for(int i=0;i<natives.length;i++){
            out.append("#define NATIVE_").append(i).append(" 0x").append(hex8((int)natives[i])).append("u\n");
        }
        out.append("\n").append(body);

        return new Result(scriptName,out.toString(),codeLen,nativesCount,staticsCount,globalsCount);
    }

    private static void appendData(StringBuilder out,long[] a){
        int cap=Math.min(a.length,256);
        for(int i=0;i<cap;i++){
            if(i%8==0) out.append("\n    ");
            out.append("0x").append(hex8((int)a[i]));
            if(i+1<cap)out.append(", ");
        }
        if(a.length>cap)out.append("\n    /* ... ").append(a.length-cap).append(" more entries ... */");
        out.append("\n");
    }

    private static long[] readWords(byte[] f,int p,int count)throws IOException{
        if(count==0)return new long[0];
        long len=(long)count*4;
        if(p<0 || p+len>f.length) throw new IOException("Tabela de dados fora do arquivo.");
        long[] a=new long[count];
        for(int i=0;i<count;i++)a[i]=u32be(f,p+i*4);
        return a;
    }

    private static byte[] loadPaged(byte[] f,int base,int listRel,int total,String what)throws IOException{
        if(total==0)return new byte[0];
        int count=(total+PAGE-1)/PAGE;
        int list=base+listRel;
        if(list<base || (long)list+count*4L>f.length) throw new IOException("Lista de páginas de "+what+" inválida.");
        byte[] out=new byte[total];
        int dst=0;
        for(int i=0;i<count;i++){
            int rel=rel(be32(f,list+i*4));
            int src=base+rel;
            int n=Math.min(PAGE,total-dst);
            if(src<base || (long)src+n>f.length) throw new IOException("Página de "+what+" fora do arquivo.");
            System.arraycopy(f,src,out,dst,n);
            dst+=n;
        }
        return out;
    }

    private static final class FunctionInfo {
        int pc, params, locals;
        String name;
        FunctionInfo(int pc,int params,int locals,String name){this.pc=pc;this.params=params;this.locals=locals;this.name=name;}
    }

    private static final class Decoder {
        final byte[] code, strings;
        final long[] natives;
        final TreeMap<Integer,FunctionInfo> functions=new TreeMap<>();
        final TreeSet<Integer> labels=new TreeSet<>();

        Decoder(byte[] code,byte[] strings,long[] natives)throws IOException{
            this.code=code;this.strings=strings;this.natives=natives;
            scan();
        }

        void scan()throws IOException{
            int pc=0;
            while(pc<code.length){
                int op=u8(code,pc);
                int size=sizeAt(code,pc);
                if(op==0x2D){
                    int params=u8(code,pc+1);
                    int locals=u16be(code,pc+2);
                    int nameLen=u8(code,pc+4);
                    String n=nameLen>0?new String(code,pc+5,nameLen,StandardCharsets.UTF_8):"func_"+hex6(pc);
                    n=ident(n,"func_"+hex6(pc));
                    functions.put(pc,new FunctionInfo(pc,params,locals,n));
                } else if(op>=0x55&&op<=0x5C){
                    int target=pc+3+s16be(code,pc+1);
                    if(target>=0&&target<code.length)labels.add(target);
                } else if(op==0x5D){
                    int target=u24be(code,pc+1);
                    if(target>=0&&target<code.length)labels.add(target);
                } else if(op==0x62){
                    int cases=u8(code,pc+1);
                    for(int i=0;i<cases;i++){
                        int ep=pc+2+i*6;
                        int target=ep+6+s16be(code,ep+4);
                        if(target>=0&&target<code.length)labels.add(target);
                    }
                }
                pc+=Math.max(1,size);
            }
        }

        String render(String script)throws IOException{
            StringBuilder out=new StringBuilder(code.length*5);
            out.append("/* Reconstructed functions for ").append(sanitizeComment(script)).append(" */\n\n");

            if(functions.isEmpty()){
                functions.put(0,new FunctionInfo(0,0,0,"script_main"));
            } else if(functions.firstKey()!=0){
                functions.put(0,new FunctionInfo(0,0,0,"script_prelude"));
            }

            List<FunctionInfo> fs=new ArrayList<>(functions.values());
            for(int fi=0;fi<fs.size();fi++){
                FunctionInfo f=fs.get(fi);
                int end=(fi+1<fs.size()?fs.get(fi+1).pc:code.length);
                renderFunction(out,f,end);
                out.append("\n");
            }
            return out.toString();
        }

        void renderFunction(StringBuilder out,FunctionInfo f,int end)throws IOException{
            out.append("static scr_int ").append(f.name).append("(");
            for(int i=0;i<f.params;i++){
                if(i>0)out.append(", ");
                out.append("scr_int arg").append(i);
            }
            out.append(") {\n");
            int localCount=Math.max(f.locals,f.params);
            if(localCount>0)out.append("    scr_int local[").append(localCount).append("] = {0};\n");

            ArrayDeque<String> stack=new ArrayDeque<>();
            int pc=f.pc;
            if(pc<end && u8(code,pc)==0x2D) pc+=sizeAt(code,pc);

            while(pc<end){
                if(labels.contains(pc)) out.append("Label_").append(hex6(pc)).append(":\n");
                int op=u8(code,pc);
                int size=sizeAt(code,pc);
                String name=OPS[op];
                String indent="    ";

                try {
                    switch(op){
                        case 0x00: out.append(indent).append("/* nop */\n"); break;
                        case 0x01: bin(stack,"+"); break;
                        case 0x02: bin(stack,"-"); break;
                        case 0x03: bin(stack,"*"); break;
                        case 0x04: bin(stack,"/"); break;
                        case 0x05: bin(stack,"%"); break;
                        case 0x06: unary(stack,"!"); break;
                        case 0x07: unary(stack,"-"); break;
                        case 0x08: bin(stack,"=="); break;
                        case 0x09: bin(stack,"!="); break;
                        case 0x0A: bin(stack,">"); break;
                        case 0x0B: bin(stack,">="); break;
                        case 0x0C: bin(stack,"<"); break;
                        case 0x0D: bin(stack,"<="); break;
                        case 0x0E: bin(stack,"+"); break;
                        case 0x0F: bin(stack,"-"); break;
                        case 0x10: bin(stack,"*"); break;
                        case 0x11: bin(stack,"/"); break;
                        case 0x12: bin(stack,"%"); break;
                        case 0x13: unary(stack,"-"); break;
                        case 0x14: bin(stack,"=="); break;
                        case 0x15: bin(stack,"!="); break;
                        case 0x16: bin(stack,">"); break;
                        case 0x17: bin(stack,">="); break;
                        case 0x18: bin(stack,"<"); break;
                        case 0x19: bin(stack,"<="); break;
                        case 0x1F: bin(stack,"&"); break;
                        case 0x20: bin(stack,"|"); break;
                        case 0x21: bin(stack,"^"); break;
                        case 0x22: if(!stack.isEmpty())stack.push("(scr_float)("+stack.pop()+")"); break;
                        case 0x23: if(!stack.isEmpty())stack.push("(scr_int)("+stack.pop()+")"); break;

                        case 0x25: stack.push(Integer.toString(u8(code,pc+1))); break;
                        case 0x26:
                            stack.push(Integer.toString(u8(code,pc+1)));
                            stack.push(Integer.toString(u8(code,pc+2))); break;
                        case 0x27:
                            stack.push(Integer.toString(u8(code,pc+1)));
                            stack.push(Integer.toString(u8(code,pc+2)));
                            stack.push(Integer.toString(u8(code,pc+3))); break;
                        case 0x28: stack.push(Integer.toString(be32(code,pc+1))); break;
                        case 0x29: stack.push(floatLiteral(be32(code,pc+1))); break;
                        case 0x2A: if(!stack.isEmpty())stack.push(stack.peek()); break;
                        case 0x2B: if(!stack.isEmpty())stack.pop(); break;

                        case 0x2C: {
                            int flags=u8(code,pc+1);
                            int argc=flags>>>2, ret=flags&3;
                            int ni=u16be(code,pc+2);
                            String[] args=popArgs(stack,argc);
                            String call="native_"+nativeName(ni)+"("+String.join(", ",args)+")";
                            if(ret>0) stack.push(call);
                            else out.append(indent).append(call).append(";\n");
                            break;
                        }
                        case 0x2E: {
                            int pop=u8(code,pc+1), ret=u8(code,pc+2);
                            if(ret==1 && !stack.isEmpty())out.append(indent).append("return ").append(stack.pop()).append(";\n");
                            else if(ret>1)out.append(indent).append("/* return ").append(ret).append(" values; stack-pop=").append(pop).append(" */ return 0;\n");
                            else out.append(indent).append("return 0;\n");
                            break;
                        }
                        case 0x2F: if(!stack.isEmpty())stack.push("*("+stack.pop()+")"); break;
                        case 0x30: {
                            String val=pop(stack), ptr=pop(stack);
                            out.append(indent).append("*(").append(ptr).append(") = ").append(val).append(";\n"); break;
                        }
                        case 0x31: {
                            String val=pop(stack), ptr=pop(stack);
                            out.append(indent).append("*(").append(ptr).append(") = ").append(val).append(";\n");
                            stack.push(val); break;
                        }

                        case 0x37: stack.push("&local["+u8(code,pc+1)+"]"); break;
                        case 0x38: stack.push("local["+u8(code,pc+1)+"]"); break;
                        case 0x39: out.append(indent).append("local[").append(u8(code,pc+1)).append("] = ").append(pop(stack)).append(";\n"); break;
                        case 0x3A: stack.push("&STATIC_DATA["+u8(code,pc+1)+"]"); break;
                        case 0x3B: stack.push("STATIC_DATA["+u8(code,pc+1)+"]"); break;
                        case 0x3C: out.append(indent).append("STATIC_DATA[").append(u8(code,pc+1)).append("] = ").append(pop(stack)).append(";\n"); break;

                        case 0x43: stack.push(Integer.toString(s16be(code,pc+1))); break;

                        case 0x4C: stack.push("&local["+u16be(code,pc+1)+"]"); break;
                        case 0x4D: stack.push("local["+u16be(code,pc+1)+"]"); break;
                        case 0x4E: out.append(indent).append("local[").append(u16be(code,pc+1)).append("] = ").append(pop(stack)).append(";\n"); break;
                        case 0x4F: stack.push("&STATIC_DATA["+u16be(code,pc+1)+"]"); break;
                        case 0x50: stack.push("STATIC_DATA["+u16be(code,pc+1)+"]"); break;
                        case 0x51: out.append(indent).append("STATIC_DATA[").append(u16be(code,pc+1)).append("] = ").append(pop(stack)).append(";\n"); break;
                        case 0x52: stack.push("&GLOBAL["+u16be(code,pc+1)+"]"); break;
                        case 0x53: stack.push("GLOBAL["+u16be(code,pc+1)+"]"); break;
                        case 0x54: out.append(indent).append("GLOBAL[").append(u16be(code,pc+1)).append("] = ").append(pop(stack)).append(";\n"); break;

                        case 0x55: jump(out,indent,pc,"",false,stack); break;
                        case 0x56: jump(out,indent,pc,"!",true,stack); break;
                        case 0x57: compareJump(out,indent,pc,"!=",stack); break;
                        case 0x58: compareJump(out,indent,pc,"==",stack); break;
                        case 0x59: compareJump(out,indent,pc,"<=",stack); break;
                        case 0x5A: compareJump(out,indent,pc,"<",stack); break;
                        case 0x5B: compareJump(out,indent,pc,">=",stack); break;
                        case 0x5C: compareJump(out,indent,pc,">",stack); break;

                        case 0x5D: {
                            int target=u24be(code,pc+1);
                            FunctionInfo callee=functions.get(target);
                            String cn=callee!=null?callee.name:"func_"+hex6(target);
                            int argc=callee!=null?callee.params:0;
                            String[] args=popArgs(stack,argc);
                            String call=cn+"("+String.join(", ",args)+")";
                            stack.push(call);
                            out.append(indent).append("/* call @0x").append(hex6(target)).append(" */\n");
                            break;
                        }
                        case 0x5E: stack.push("&GLOBAL["+u24be(code,pc+1)+"]"); break;
                        case 0x5F: stack.push("GLOBAL["+u24be(code,pc+1)+"]"); break;
                        case 0x60: out.append(indent).append("GLOBAL[").append(u24be(code,pc+1)).append("] = ").append(pop(stack)).append(";\n"); break;
                        case 0x61: stack.push(Integer.toString(sign24(u24be(code,pc+1)))); break;
                        case 0x62: {
                            String expr=pop(stack); int cases=u8(code,pc+1);
                            out.append(indent).append("switch (").append(expr).append(") {\n");
                            for(int i=0;i<cases;i++){
                                int ep=pc+2+i*6; int value=be32(code,ep); int target=ep+6+s16be(code,ep+4);
                                out.append(indent).append("case ").append(value).append(": goto Label_").append(hex6(target)).append(";\n");
                            }
                            out.append(indent).append("}\n"); break;
                        }
                        case 0x63: {
                            String idx=pop(stack);
                            try {
                                int si=Integer.decode(idx);
                                stack.push("\""+escapeString(stringAt(si))+"\"");
                            } catch(Exception e){stack.push("string_at("+idx+")");}
                            break;
                        }
                        case 0x64: if(!stack.isEmpty())stack.push("joaat("+stack.pop()+")"); break;
                        case 0x65: out.append(indent).append("/* strcpy-like opcode */\n"); break;
                        case 0x66: if(!stack.isEmpty())stack.push("int_to_string("+stack.pop()+")"); break;
                        case 0x67: binFunc(stack,"str_add"); break;
                        case 0x68: binFunc(stack,"str_add_int"); break;
                        case 0x69: out.append(indent).append("/* memcpy-like opcode */\n"); break;
                        case 0x6A: out.append(indent).append("/* catch */\n"); break;
                        case 0x6B: out.append(indent).append("throw_value(").append(pop(stack)).append(");\n"); break;
                        case 0x6C: out.append(indent).append("/* indirect call: ").append(pop(stack)).append(" */\n"); break;

                        default:
                            if(op>=0x6D&&op<=0x75) stack.push(Integer.toString(op-0x6E));
                            else if(op>=0x76&&op<=0x7E) stack.push(Float.toString((float)(op-0x77))+"f");
                            else if(isU8Operand(op)) out.append(indent).append("/* ").append(name).append(" ").append(u8(code,pc+1)).append(" */\n");
                            else if(isU16Operand(op)) out.append(indent).append("/* ").append(name).append(" ").append(u16be(code,pc+1)).append(" */\n");
                            else out.append(indent).append("/* ").append(name).append(" @0x").append(hex6(pc)).append(" */\n");
                    }
                } catch(NoSuchElementException ex){
                    out.append(indent).append("/* stack underflow while reconstructing ").append(name).append(" @0x").append(hex6(pc)).append(" */\n");
                    stack.clear();
                }

                pc+=Math.max(1,size);
            }

            if(!stack.isEmpty()){
                out.append("    /* expression(s) left on VM stack: ");
                int n=0; for(String s:stack){if(n++>0)out.append(" | ");out.append(s);if(n==4)break;}
                out.append(" */\n");
            }
            out.append("}\n");
        }

        private void jump(StringBuilder out,String indent,int pc,String prefix,boolean conditional,Deque<String> stack){
            int target=pc+3+s16be(code,pc+1);
            if(!conditional)out.append(indent).append("goto Label_").append(hex6(target)).append(";\n");
            else out.append(indent).append("if (").append(prefix).append("(").append(pop(stack)).append(")) goto Label_").append(hex6(target)).append(";\n");
        }
        private void compareJump(StringBuilder out,String indent,int pc,String op,Deque<String> stack){
            int target=pc+3+s16be(code,pc+1);
            String b=pop(stack),a=pop(stack);
            out.append(indent).append("if ((").append(a).append(") ").append(op).append(" (").append(b).append(")) goto Label_").append(hex6(target)).append(";\n");
        }

        private String nativeName(int idx){
            if(idx>=0&&idx<natives.length)return "0x"+hex8((int)natives[idx]);
            return "INDEX_"+idx;
        }
        private String stringAt(int idx){
            if(idx<0||idx>=strings.length)return "<bad_string_"+idx+">";
            int e=idx;while(e<strings.length&&strings[e]!=0)e++;
            return new String(strings,idx,e-idx,StandardCharsets.UTF_8);
        }
    }

    private static int sizeAt(byte[] c,int pc)throws IOException{
        int op=u8(c,pc);
        int size;
        if(op==0x25)size=2;
        else if(op==0x26)size=3;
        else if(op==0x27)size=4;
        else if(op==0x28||op==0x29)size=5;
        else if(op==0x2C)size=4;
        else if(op==0x2D){
            ensure(c,pc,5); size=5+u8(c,pc+4);
        } else if(op==0x2E)size=3;
        else if(isU8Operand(op))size=2;
        else if(op==0x43)size=3;
        else if(isU16Operand(op))size=3;
        else if(op>=0x55&&op<=0x5C)size=3;
        else if(op==0x5D||op==0x5E||op==0x5F||op==0x60||op==0x61)size=4;
        else if(op==0x62){
            ensure(c,pc,2); size=2+u8(c,pc+1)*6;
        } else size=1;
        ensure(c,pc,size);
        return size;
    }

    private static boolean isU8Operand(int op){ return (op>=0x34&&op<=0x42); }
    private static boolean isU16Operand(int op){ return (op>=0x44&&op<=0x54); }

    private static void ensure(byte[] b,int p,int n)throws IOException{
        if(p<0||n<0||(long)p+n>b.length)throw new IOException("Opcode truncado em 0x"+hex6(p));
    }
    private static void bin(Deque<String>s,String op){String b=pop(s),a=pop(s);s.push("(("+a+") "+op+" ("+b+"))");}
    private static void unary(Deque<String>s,String op){s.push("("+op+"("+pop(s)+"))");}
    private static void binFunc(Deque<String>s,String fn){String b=pop(s),a=pop(s);s.push(fn+"("+a+", "+b+")");}
    private static String pop(Deque<String>s){return s.isEmpty()?"/*stack?*/0":s.pop();}
    private static String[] popArgs(Deque<String>s,int n){
        String[]a=new String[n];
        for(int i=n-1;i>=0;i--)a[i]=pop(s);
        return a;
    }

    private static int positive(int v,String n)throws IOException{
        if(v<0)throw new IOException(n+" inválido: "+Integer.toUnsignedString(v));
        return v;
    }
    private static int rel(int v){return v&0x0FFFFFFF;}
    private static int u8(byte[]b,int p){return b[p]&255;}
    private static int u16be(byte[]b,int p){return (u8(b,p)<<8)|u8(b,p+1);}
    private static int s16be(byte[]b,int p){return (short)u16be(b,p);}
    private static int u24be(byte[]b,int p){return (u8(b,p)<<16)|(u8(b,p+1)<<8)|u8(b,p+2);}
    private static int sign24(int x){return (x&0x800000)!=0?x|0xFF000000:x;}
    private static int be32(byte[]b,int p){return (u8(b,p)<<24)|(u8(b,p+1)<<16)|(u8(b,p+2)<<8)|u8(b,p+3);}
    private static long u32be(byte[]b,int p){return be32(b,p)&0xFFFFFFFFL;}

    private static String safeCString(byte[]b,int p,int end){
        if(p<0||p>=end)return "";
        int e=p;while(e<end&&b[e]!=0&&e-p<512)e++;
        return new String(b,p,e-p,StandardCharsets.UTF_8);
    }
    private static String ident(String s,String fallback){
        if(s==null||s.isEmpty())return fallback;
        StringBuilder o=new StringBuilder();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if((i==0&&Character.isJavaIdentifierStart(c))||(i>0&&Character.isJavaIdentifierPart(c)))o.append(c);
            else o.append('_');
        }
        String r=o.toString();
        if(r.isEmpty()||Character.isDigit(r.charAt(0)))return fallback+"_"+r;
        return r;
    }
    private static String sanitizeComment(String s){return s.replace("*/","* /").replace("\r"," ").replace("\n"," ");}
    private static String escapeString(String s){
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t");
    }
    private static String floatLiteral(int bits){
        float f=Float.intBitsToFloat(bits);
        if(Float.isNaN(f))return "NAN";
        if(Float.isInfinite(f))return f>0?"INFINITY":"-INFINITY";
        return Float.toString(f)+"f";
    }
    private static String hex2(int v){return String.format(Locale.US,"%02X",v&255);}
    private static String hex6(int v){return String.format(Locale.US,"%06X",v&0xFFFFFF);}
    private static String hex8(int v){return String.format(Locale.US,"%08X",v);}
}
