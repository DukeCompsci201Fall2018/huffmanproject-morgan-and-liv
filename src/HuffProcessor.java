//Livia Seibert
//Morgan Langenhagen

import java.util.*;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	
	

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out)
	{
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makingCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		
		out.close();
	}
	
	private int[] readForCounts (BitInputStream in)
	{
		int[] freq = new int[ALPH_SIZE +1];
		int val=in.readBits(BITS_PER_WORD);
		
		while(val!=-1)
		{
			freq[val]++;
			val=in.readBits(BITS_PER_WORD);
		}
		
		freq[PSEUDO_EOF]=1;
		return freq;
	}
	
	private HuffNode makeTreeFromCounts(int[] freq)
	{
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		
		for(int i=0; i<freq.length; i++)
		{
			if(freq[i]>0)
				pq.add(new HuffNode(i, freq[i], null, null));
		}
		
		while(pq.size()>1)
		{
			HuffNode left=pq.remove();
			HuffNode right=pq.remove();
			
			HuffNode t = new HuffNode(-1, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		
		HuffNode root = pq.remove();
		
		return root;

	}
	
	private String[] makingCodingsFromTree(HuffNode root)
	{
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(encodings, root, "");
		return encodings;
		
	}
	
	private void codingHelper(String[] encode, HuffNode root, String path)
	{
		if(root==null)
			return;
		
		if(root.myLeft==null && root.myRight==null)
		{
			encode[root.myValue]=path;
			return;
		}
		
		codingHelper(encode, root.myLeft, path+"0");
		codingHelper(encode, root.myRight, path+"1");
		
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out)
	{
		if (root.myLeft==null && root.myRight==null) {
			out.writeBits(1, 1);
			out.writeBits(9, BITS_PER_WORD+1);
		}
		else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
	}
	
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out)
	{
		int val=in.readBits(BITS_PER_WORD);
		
		while(val!=-1)
		{
			out.writeBits(codings[val].length(), Integer.parseInt(codings[val], 2));
			val=in.readBits(BITS_PER_WORD);
		}
		
		out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF], 2));

		
		/*for (int i=0; i<codings.length; i++) {
			String code = codings[in.readBits(BITS_PER_WORD)];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			code = codings[PSEUDO_EOF];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
		}*/
	}
	
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out)
	{
		int val = in.readBits(BITS_PER_INT);
		if (val != HUFF_TREE) 
			throw new HuffException("illegal header starts with "+val);
		//out.writeBits(BITS_PER_WORD, val);
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream in)
	{
		//System.out.println("FOUND");
		int bit = in.readBits(1);
		//System.out.println("HELLO");
		
		if(bit==-1)
			throw new HuffException("bad input");
		if(bit==0)
		{
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		
		else
		{
			int value=in.readBits(BITS_PER_WORD +1);
			return new HuffNode(value, 0, null, null);
		}
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out)
	{
		//System.out.println("TEST");
		
		HuffNode current = root;
		while(true)
		{
			int bits = in.readBits(1);
			//System.out.println("here");
			if(bits==-1)
				throw new HuffException("bad input, no PSEUDO_EOF");
			else
			{
				if(bits==0)
					current = current.myLeft;
				else
					current = current.myRight;
				
				if(current.myRight==null && current.myLeft==null)
				{
					if(current.myValue==PSEUDO_EOF)
						break;
					else
					{
						out.writeBits(BITS_PER_WORD, current.myValue);
						current=root;
					}
				}
			}
			
		}
	}
}