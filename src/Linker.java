import java.util.*;

/**
 * Operating Systems Lab 1 - Two Pass Linker
 * By William Shi
 * NetID: ws837
 *
 * This is a two pass linker program which relocates relative addresses, creates a symbol table for all symbols, and
 * resolves external references.
 *
 * This is done by taking in the input text file, manipulating the contents to be of an array list consisting of
 * coherent lines, and then processing said array list.
 *
 * The first pass produces a symbol table and stores the base address of each module in the input file.
 *
 * The second pass uses the base addresses and symbol table produced in the first pass and relocates the relative
 * addresses and resolves external references.
 *
 * Error checking is done by keeping track of errors occurring in the program and storing them in relevant data
 * structures. Once the file is processed, the error checking data structures are used to find any errors that
 * occurred during processing, and if any such error is found, it is printed alongside the relevant memory address or
 * symbol that the error occurs for.
 *
 */
public class Linker {

    // symbol table to hold all the symbols and their corresponding definition addresses
    private static Map<String, Integer> symbolTable = new LinkedHashMap<>();

    // keeps track of which module the symbols are defined in
    private static Map<String, Integer> symbolDefinitionLocation = new HashMap<>();

    // keeps track of symbols that are defined outside the length of the module (error checking)
    private static Map<String, Integer> definedOutsideModuleLength = new HashMap<>();

    // keeps track of addresses that are on the use chain but not of type E (error checking)
    private static Map<Integer, String> onUseListNotE = new HashMap<>();

    // keeps track of symbols that are used but not defined (error checking)
    private static Map<Integer, String> usedButNotDefinedSymbols = new HashMap<>();

    // keeps track of symbols that are defined more than once (error checking)
    private static ArrayList<String> multiplyDefinedSymbols = new ArrayList<>();

    // keeps track of all used symbols
    private static ArrayList<String> usedSymbols = new ArrayList<>();

    // keeps track of the addresses where the chain terminates due to pointing to an address outside
    // the module size (error checking)
    private static ArrayList<Integer> exceedsModuleSizeChainTerminated = new ArrayList<>();

    // keeps track of addresses that are of type E but are not part of the use chain (error checking)
    private static ArrayList<Integer> notOnUseListEType = new ArrayList<>();

    // arbitrary limit to character size for a symbol
    final private static int SYMBOL_CHARACTER_LIMIT = 8;


    // takes in the standard input as the file name and processes the contents of said file
    public static void main(String args[]){
        String text = "";            // holds all the text of the inputted file
        ArrayList<String> allText,   // holds all the text in an array delimited by whitespace
                          input;     // holds all the text in coherent lines after formatting allText

        Scanner scanner = new Scanner(System.in);

        // store the whole input text to be processed and formatted
        while (scanner.hasNext()){
            text += " " + scanner.next();
        }

        // creates an array consisting of all strings delimited by whitespace in the input file
        allText = new ArrayList<String>(Arrays.asList(text.split("\\s+")));

        // sometimes, when the first character in the file is a space, there will be a blank string when splitting
        if (allText.get(0).equals("")){
            allText.remove(0);
        }

        // format the array of strings into coherent lines for parsing
        input = formatToLines(allText);

        // perform the first pass
        ArrayList<Integer> moduleBaseAddresses = firstPass(input);

        // perform the second pass
        ArrayList<Integer> memoryMap = secondPass(input, moduleBaseAddresses);

        // print the results
        printResults(memoryMap);

    }

    /**
     * Formats an array list of all strings in the input file into coherent lines for linker parsing.
     *
     * @param allText
     *
     * @return the formatted array list of coherent linker lines
     */
    private static ArrayList<String> formatToLines(ArrayList<String> allText){
        ArrayList<String> input = new ArrayList<>();    // the return list
        String workingLine = "";                        // current working line
        int nextLineIndex = 0;                          // keeps track of where the next line should begin
        int lineLength = -1;                            // length of the current line

        for (int i = 0; i < allText.size(); i++){
            // if we're at a new line
            if (i == nextLineIndex){

                // we only add a new line after we're done working on the previous one, so exclude 0
                if (nextLineIndex != 0){
                    input.add(workingLine);
                }

                lineLength = Integer.parseInt(allText.get(i));

                // 2 times line length is needed because each address is split in 2 parts
                // 1 plus is needed because we omit the first number telling us the length
                nextLineIndex += (1+(2*lineLength));

                workingLine = allText.get(i) + " ";
            }
            // otherwise, we're still going through the current working line
            else {
                workingLine += allText.get(i) + " ";
            }
        }

        //add the final line
        input.add(workingLine);

        return input;
    }

    /**
     * Prints the results of the linker, including error checking, symbol table, and memory map.
     *
     * @param memoryMap
     */
    private static void printResults(ArrayList<Integer> memoryMap){

        // Symbol table printing
        System.out.println("Symbol Table");
        for (String symbol : symbolTable.keySet()){

            System.out.print(symbol + " = " + symbolTable.get(symbol));

            if (multiplyDefinedSymbols.contains(symbol)){
                System.out.print(" Error: This variable is multiply defined; first value used.");
            }
            else if (definedOutsideModuleLength.containsKey(symbol)){
                System.out.format(" Error: The value of %s is outside of module %d; zero (relative) used",
                                         symbol, definedOutsideModuleLength.get(symbol));
            }

            System.out.println();
        }

        System.out.println();



        // Memory map printing
        System.out.println("Memory Map");
        for (int i = 0; i < memoryMap.size(); i++){
            String index = i + ":";
            System.out.format("%-4s", index);
            System.out.print(memoryMap.get(i));

            if (usedButNotDefinedSymbols.containsKey(i)){
                System.out.format(" Error: %s is not defined; zero used instead", usedButNotDefinedSymbols.get(i));
            }
            else if (exceedsModuleSizeChainTerminated.contains(i)){
                System.out.print(" Error: Pointer in use chain exceeds module size; chain terminated.");
            }
            else if (onUseListNotE.containsKey(i)){
                System.out.format(" Error: %s type address on use chain; treated as E type.", onUseListNotE.get(i));
            }
            else if (notOnUseListEType.contains(i)){
                System.out.print(" Error: E type address not on use chain; treated as I type.");
            }

            System.out.println();
        }

        // Post memory map printing if needed (warnings)
        // add usedButNotDefinedSymbols because those are not in the symbol table or usedSymbols but are used
        if (usedSymbols.size()+usedButNotDefinedSymbols.size() != symbolTable.size()) {
            System.out.println();

            for (String symbol : symbolTable.keySet()) {
                if (!usedSymbols.contains(symbol)) {
                    System.out.format("Warning: %s was defined in module %d but never used.",
                                             symbol, symbolDefinitionLocation.get(symbol));
                    System.out.println();
                }
            }
        }
    }

    /**
     * First pass of the linker which returns an array list with the base addresses of each module and
     * also creates the symbol table for the working input.
     *
     * @param input
     *
     * @return an array list with the base addresses of each module
     */
    private static ArrayList<Integer> firstPass(ArrayList<String> input){
        ArrayList<Integer> moduleBaseAddresses = new ArrayList<>();     // return array list

        int currentBaseAddress = 0;     // keeps track of what the current base address is
        int currentModule = 0;          // keeps track of which module we are currently in

        for (int i = 0; i < input.size(); i++){
            String[] workingLineParts = input.get(i).split("\\s+");

            // definition list
            if (i % 3 == 0){
                // the length of the definition list
                int length = Integer.parseInt(workingLineParts[0]);

                // gets the length of the module from the guaranteed program text 2 lines after the definition list
                // this is used for figuring out whether the definition is within the module length
                int moduleLength = Integer.parseInt(input.get(i+2).split("\\s+")[0]);

                for (int j = 0; j < length; j++){
                    // the symbol being defined
                    String symbol = workingLineParts[1+(2*j)];

                    // the relative address of the definition of the symbol
                    int symbolRelativeAddress = Integer.parseInt(workingLineParts[2+(2*j)]);

                    // error checking for whether the definition is within the module length
                    if (symbolRelativeAddress > moduleLength - 1){
                        symbolRelativeAddress = 0;
                        definedOutsideModuleLength.put(symbol, currentModule);
                    }

                    // get the absolute address
                    symbolRelativeAddress += currentBaseAddress;

                    // error checking for multiply defined symbols
                    if (symbolTable.containsKey(symbol)){
                        multiplyDefinedSymbols.add(symbol);
                    }
                    else{
                        symbolTable.put(symbol, symbolRelativeAddress);
                        symbolDefinitionLocation.put(symbol, currentModule);
                    }
                }
            }

            // program text
            else if (i % 3 == 2){
                int length = Integer.parseInt(workingLineParts[0]);

                moduleBaseAddresses.add(currentBaseAddress);

                currentBaseAddress += length;

                currentModule++;
            }
        }

        return moduleBaseAddresses;
    }

    /**
     * Second pass of the linker which returns a list with the memory addresses in order and correctly
     * transformed and modified.
     *
     * @param input
     *
     * @param moduleBaseAddresses
     *
     * @return a list with the memory addresses in order and correctly transformed and modified
     */
    private static ArrayList<Integer> secondPass(ArrayList<String> input, ArrayList<Integer> moduleBaseAddresses){
        ArrayList<Integer> memoryMap = new ArrayList<>();       // returned array list
        HashMap<String, Integer> addressMap = new HashMap<>();  // keeps track of symbol uses

        int currentBaseAddress = 0;     // keeps track of what the current base address is
        int moduleNumber = 0;           // keeps track of which module we are currently in

        for (int i = 0; i < input.size(); i++){
            String[] workingLineParts = input.get(i).split("\\s+");
            //ArrayList<Integer> useAddress = new ArrayList<>();

            // use list is needed to assign symbols to addresses in the program text
            if (i % 3 == 1){
                int uses = Integer.parseInt(workingLineParts[0]);   // number of uses
                String symbol;
                int relAddr;

                // this associates all the symbols with which relative address they're used in
                for (int j = 0; j < uses; j++){
                    symbol = workingLineParts[1+(2*j)];
                    relAddr = Integer.parseInt(workingLineParts[2+(2*j)]);

                    // error checking whether the used symbol is defined
                    if (!symbolTable.containsKey(symbol)){
                        usedButNotDefinedSymbols.put(relAddr+currentBaseAddress, symbol);
                    }

                    addressMap.put(symbol, relAddr);

                    // add the used symbol to the usedSymbols list for later checking
                    if (!usedSymbols.contains(symbol)) {
                        usedSymbols.add(symbol);
                    }
                }
            }

            // program text
            else if (i % 3 == 2){
                // workingLineParts[0] is the first digit of the prgm txt line, indicating the total number of addresses
                int length = Integer.parseInt(workingLineParts[0]);

                ArrayList<Integer> chainAddresses = new ArrayList<>();  // addresses of those in a use chain
                ArrayList<Integer> allEAddresses = new ArrayList<>();   // addresses of all E type addresses

                for (int j = 0; j < length; j++){
                    // for a line such as R 2004,
                    //      code     holds the R
                    //      address  holds the 2004
                    String code = workingLineParts[1+(2*j)];
                    String address = workingLineParts[2+(2*j)];

                    // transform the R addresses immediately
                    if (code.equals("R")){
                        int intAddress = Integer.parseInt(address);
                        intAddress += moduleBaseAddresses.get(moduleNumber);

                        memoryMap.add(intAddress);
                    }
                    // add these to the map
                    else if (code.equals("A")){
                        memoryMap.add(Integer.parseInt(address));
                    }
                    // add these to the map
                    else if (code.equals("I")){
                        memoryMap.add(Integer.parseInt(address));
                    }
                    // don't do anything for these yet, but add them to keep track of all E type addresses
                    else if (code.equals("E")){
                        allEAddresses.add(j + moduleBaseAddresses.get(moduleNumber));

                        memoryMap.add(Integer.parseInt(address));
                    }
                    else{
                        System.out.println("error");
                    }
                }

                // go through each symbol present on the use list
                for (String symbol : addressMap.keySet()){

                    // symAddr holds the relative address of the use of the symbol
                    int symAddr = addressMap.get(symbol);

                    // code holds the single letter code (R, I, E, A) indicating the type of address
                    String code = workingLineParts[1+(2*symAddr)];

                    // addr holds the string of the 4 digit address
                    String addr;

                    // Start parsing use symbols here
                    boolean running = true;

                    while(running){
                        code = workingLineParts[1+(2*symAddr)];
                        addr = workingLineParts[2+(2*symAddr)];

                        // error checking for symbols that are used but not defined
                        if (usedButNotDefinedSymbols.containsValue(symbol)){
                            usedButNotDefinedSymbols.put(symAddr+currentBaseAddress, symbol);
                        }

                        // error checking for addresses that are part of the chain but not of type E
                        if (!code.equals("E")){
                            onUseListNotE.put(moduleBaseAddresses.get(moduleNumber)+symAddr, code);
                        }

                        String location = addr.substring(1);    // the last 3 digits of 4 digit word

                        chainAddresses.add(symAddr+moduleBaseAddresses.get(moduleNumber));

                        // termination code
                        if (location.equals("777")){
                            String value;

                            if (null != symbolTable.get(symbol)) {
                                value = String.format("%03d", symbolTable.get(symbol));
                            }
                            else{
                                value = "000";
                            }
                            addr = addr.substring(0, 1) + value;

                            memoryMap.set(moduleBaseAddresses.get(moduleNumber)+symAddr, Integer.parseInt(addr));

                            break;
                        }
                        // if the use address is greater than the length of the module
                        else if (Integer.parseInt(location) > length - 1){
                            exceedsModuleSizeChainTerminated.add(symAddr + currentBaseAddress);

                            String value;

                            if (null != symbolTable.get(symbol)) {
                                value = String.format("%03d", symbolTable.get(symbol));
                            }
                            else{
                                value = "000";
                            }
                            addr = addr.substring(0, 1) + value;

                            memoryMap.set(moduleBaseAddresses.get(moduleNumber)+symAddr, Integer.parseInt(addr));

                            break;
                        }
                        // otherwise, change the address to the symbol value and then go to the next link
                        else{
                            int symbolValue;

                            // in case the symbol is not defined, we set it to default 0
                            if (null != symbolTable.get(symbol)) {
                                symbolValue = symbolTable.get(symbol);
                            }
                            else{
                                symbolValue = 0;
                            }

                            String newAddr = String.format("%03d", symbolValue);

                            addr = addr.substring(0, 1) + newAddr;

                            memoryMap.set(moduleBaseAddresses.get(moduleNumber)+symAddr, Integer.parseInt(addr));

                            // this gives us the location of the next part of the chain
                            symAddr = Integer.parseInt(location);
                        }
                    }
                }

                // checks all E types against those that participated in the chain to find E types not on the chain
                for (int address : allEAddresses){
                    if (!chainAddresses.contains(address)){
                        notOnUseListEType.add(address);
                    }
                }

                currentBaseAddress += length;

                moduleNumber++;

                // need to clear after every module
                addressMap.clear();
            }
        }

        return memoryMap;
    }

}
