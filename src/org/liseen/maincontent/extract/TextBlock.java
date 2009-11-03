package org.liseen.maincontent.extract;

import org.htmlparser.Node;

public class TextBlock {
	// block's text
	public String text;
	
	// the number of tags after a block 
	public int contTagNum = 0;
	
	// the length of a block's anchor text
	public int anchorTextLen = 0;
	
	// the length of a block's non anchor Chinese text
	public int noAnchorChineseTextLen = 0;
	
	// the number of tags in this block
	public int tagNum = 0;
	
	// the node of this block
	public Node blockNode = null;
}
