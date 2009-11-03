package org.liseen.maincontent.extract;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.htmlparser.*;
import org.htmlparser.nodes.TagNode;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.*;

import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.liseen.maincontent.extract.Util;


public class Extractor {
	
	public final static int TextSizeLimitExcepteAnchor = 40;
	public final static int TextBlockSizeLimit = 10;
	public final static int ConTagDenstiyLimit = 4;
	public final static int TagDenstiyLimit = 7;
	public final static int GoodBlockTagDenstiyLimit = 8;
	public final static int GoodBlockMaxInterval = 10;
	
	public final static String stripSpaceRegex = "(?:\\s|\\w)";
	public final static Pattern stripSpacePattern = Pattern.compile(stripSpaceRegex);
	
	public final static String blockTagNameRegex = "^(?:DIV|LI|H\\d|LI|UL|TABLE|SPAN|TR|P)$";
	public final static Pattern blockTagNamePattern = Pattern.compile(blockTagNameRegex, Pattern.CASE_INSENSITIVE);
	
    public static final String authorRegex = "(?:作者|编辑)\\s*(?::|：)\\s*(?:中关村在线\\s+)?([^\\s]{2,20})(?:$|\\s|\\d)";
    public static final Pattern authorPattern = Pattern.compile(authorRegex);

    public static final String dateRegex =  "((?:19|20)?\\d{2})\\s*(?:年|－|-|\\.)\\s*(\\d{1,2})\\s*(?:月|－|-|\\.)\\s*(\\d{1,2})\\s*(?:日|号|.|\\s)?";
    public static final Pattern datePattern = Pattern.compile(dateRegex);
    
    public static final String stripScriptStyleRegex = "(?:<script.*?</script>|<style>[\\s\\S]*?</style>)";
	public static final Pattern  stripScriptStylePattern = Pattern.compile(stripScriptStyleRegex, Pattern.CASE_INSENSITIVE|Pattern.DOTALL|Pattern.MULTILINE);

	public static final String stripHtmlRegex = "(?:<[^>]*?>|&nbsp;)";
	public static final Pattern stripHtmlPattern = Pattern.compile(stripHtmlRegex, Pattern.CASE_INSENSITIVE|Pattern.DOTALL|Pattern.MULTILINE);
	
	//public static final String filterNoiseRegex = "(?:CopyRight|下一篇).*$";
	//public static final Pattern filterNoisePattern = Pattern.compile(stripHtmlRegex, Pattern.CASE_INSENSITIVE|Pattern.DOTALL|Pattern.MULTILINE);
	
	//public static final String extractTitleRegex = "(?:[_——-－\\|].+$|www\\..+?\\.com)";
	public static final String extractTitleRegex = "(?:[\\_\\-——\\|][^\\_\\-——\\|]+$|(?:http://)?www\\..+?\\.com|第\\d页)";
	public static final Pattern extractTitlePattern = Pattern.compile(extractTitleRegex, Pattern.CASE_INSENSITIVE);
	
	public static Extractor extractor = null;
	
	private ArrayList<TextBlock> blockList = new ArrayList<TextBlock>();
	
	// must be initial every extracts
	private boolean prevIsText = false;	
	private StringBuffer textBuf = new StringBuffer();
	private TextBlock currTextBlock = null;
	private int currTagNum = 0;
	
	private Extractor() {};
	
	public static Extractor getInstance() {
		if (extractor == null)
			extractor = new Extractor();
		return extractor;
	}
	
	public  void initExtractor() {
		blockList.clear();
		prevIsText = false;	
		textBuf.delete(0, textBuf.length());
		currTextBlock = null;
	}
	
	public  Page extract(String url, String html) {
		Page page = new Page();
		page.url = url;
		page.rawHtml = stripScriptStylePattern.matcher(html).replaceAll("");

		if (extract(page)) {
			return page;
		} else {
			return null;
		}
	}
	
	public  boolean extract(Page page) {
		// init
		initExtractor();
		// parse html
		NodeList nodeList = getDomTree(page);
		if (nodeList == null) {
			Util.logErr("Get dom tree failed");
			return false;
		}
		
		Node html = null;
		Node body = null;
		Node head = null;
		
		for (Node node: nodeList.toNodeArray()) {
			if (node instanceof Html) {
				html = node;
			}
		}
		
		if (html == null) {
			Util.logErr("Get <HTML> node failed");
			return false;
		}
		
		if (html.getChildren() != null) {
			for (Node node: html.getChildren().toNodeArray()) {
				if (node instanceof BodyTag) {
					body = node;
				}
				
				if (node instanceof HeadTag) {
					head = node;
				}
			}
		}
		
		if (body == null) {
			Util.logErr("Get <HTML> node failed");
			return false;
		}
		
		// extract text blocks
		extractTextBlockList(body.getChildren(), false);
		if (blockList == null || blockList.size() == 0) {
			Util.logErr("Extract text block failed");
			return false;
		}
		
		// compute text block's non anchor chinese text length
		for (int i = 0; i < blockList.size(); i++) {
			TextBlock block = blockList.get(i);

			block.noAnchorChineseTextLen = stripSpacePattern.matcher(block.text).replaceAll("").length() - block.anchorTextLen;
			
			Util.debug("=============================== block list: " + i);
			Util.debug("number of block tag:\t" + block.contTagNum);
			Util.debug("length of anchor text:\t" + block.anchorTextLen);
			Util.debug("length of none anchor text:\t" + block.noAnchorChineseTextLen);
			Util.debug("count of tag:\t" + block.tagNum);	
			Util.debug(block.text);
		};
		
		// find good text blocks;
		ArrayList<BlockRange> goodBlockRangeList = this.getGoodBlockRange();
		if (goodBlockRangeList == null || goodBlockRangeList.size() == 0) {
			Util.logErr("Find good text blocks failed");
			return false;
		}

		// compute good text block range
		boolean computeRet = computeGoodBlockRange(goodBlockRangeList);
		if (!computeRet) {
			Util.logErr("Compute good text block range failed");
			return false;
		}
		
		for (int i = 0; i < goodBlockRangeList.size(); i++) {
			BlockRange range = goodBlockRangeList.get(i);
			Util.debug("=============================== good block range list: " + i);
			Util.debug("range.isBest:\t" + range.isBest);
			Util.debug("range.good:\t" + range.goodBlockIdx);
			Util.debug("range.start:\t" + range.start);
			Util.debug("range.end:\t" + range.end);
		}
		
		// filter some tail noise
		/*
		int loopNum = 0;
		if (maxIdx - minIdx > 5) {
			loopNum = 2;
		} else {
			loopNum = 1;
		}
	
			int tmpMaxIdx = maxIdx;
			for (int i = 0; i < loopNum; i++ ) {
				TextBlock block = blockList.get(tmpMaxIdx - i);
				block.text = filterNoisePattern.matcher(block.text).replaceAll("");
				System.err.println("=======================" + block.text);
				// TODO compile a regex for it
				Matcher noiseMatcher = filterNoisePattern.matcher(block.text);
				if (noiseMatcher.find()) {
					maxIdx--;
				}
				if (block.text.contains("下一篇")) {
				
					maxIdx--;
				}
				// TODO if the anchor ratio very larger, delete the block
			}
		*/
		
		// compute total text block range
		Integer blockStart = null;
		StringBuffer contentBuf = new StringBuffer();
		
		for (BlockRange range: goodBlockRangeList) {
			if (range.start != -1 && range.end != -1) {
				if (blockStart == null)
					blockStart = new  Integer(range.start);
				for (int i = range.start; i <= range.end; i++) {
					contentBuf.append(blockList.get(i).text);;
				}
			}
		}
		
		page.mainContent = contentBuf.toString();
		
		if (blockStart == null) {
			Util.logErr("Get block index start!");
			return false;
		}
		// extract author and pub-time
		StringBuffer headBuf = new StringBuffer();
		
		int headLimit = blockStart.intValue();
			
		for (int i = 0; i <= headLimit; i++) {
			TextBlock block = blockList.get(i);
			headBuf.append(" ");
			headBuf.append(block.text);			
		}
		
		//
		//headBuf.append(" ");
		//headBuf.append(blockList.get(maxIdx).text);
		//
		
		String beforeStr = headBuf.toString();
		//System.err.println("beforestr:" + beforeStr);

		if (page.author == null) {
			Matcher authorMatcher = authorPattern.matcher(beforeStr);
			if (authorMatcher.find()) {
				page.author = authorMatcher.group(1);
			}
		}
		if (page.pubTime == null) {
			Matcher dateMatcher = datePattern.matcher(beforeStr);
			if (dateMatcher.find()) {
				String year = dateMatcher.group(1);
				if (year.length() == 2) {
					if (year.startsWith("0")) {
						year = "20" + year;
					} else {
						year = "19" + year;
					}
				}
				
				page.pubTime = year + "-"
						+ dateMatcher.group(2) + "-" + dateMatcher.group(3);
			}
		}
	
		page.title = this.extractTitle(head);
		
		return true;
	}
	
	public String extractTitle(Node headNode) {
		Node titleNode = extractTitleNode(headNode);
		if (titleNode != null) {
//			return extractTitlePattern.matcher(titleNode.toPlainTextString()).replaceAll("");
			
			String[] tas = titleNode.toPlainTextString().split("[\\_\\-——\\|]");
			if (tas.length > 1) {
				int maxLength = -1;
				int maxIdx = -1;
				for (int i = 0; i < tas.length - 1; i++) {
					int len = stripSpacePattern.matcher(tas[i]).replaceAll("").length();
					if (len > maxLength) {
						maxLength = len;
						maxIdx = i;
					}
				}
				
				if (maxIdx > -1) {
					return tas[maxIdx];
				}
			}
			else if (tas.length == 1) {
				int len = stripSpacePattern.matcher(tas[0]).replaceAll("").length();
				if (len < 6) {
					return null;
				} else {
					return tas[0];
				}
			}
		}
		
		return null;
	}
	
	public  Node extractTitleNode(Node node) {
		if (node == null)
			return null;
		
		if (node instanceof TitleTag) 
			return node;
		
		if (node.getChildren() == null)
			return null;
		
		for (Node n: node.getChildren().toNodeArray()) {
			Node title = extractTitleNode(n);
			if (title != null) {
				return title;
			}
		}
		return null;
	}
	
		
	public void mergeGoodBlockRange() {
		
	}
	
	public boolean computeGoodBlockRange(ArrayList<BlockRange> goodBlockRangeList) {
		// get the best text block
		BlockRange bestBlockRange = null;
		int blockRangeIdx = -1;
		for (int i = 0; i < goodBlockRangeList.size(); i++) {
			if (goodBlockRangeList.get(i).isBest) {
				blockRangeIdx = i;
				bestBlockRange = goodBlockRangeList.get(i);
			}
		}
	
		if (bestBlockRange == null) {
			Util.logErr("None best text block found!");
			return false;
		}
		
		// first, compute the best text block
		bestBlockRange.start = bestBlockRange.goodBlockIdx;
		bestBlockRange.end = bestBlockRange.goodBlockIdx;
		for (int i = bestBlockRange.goodBlockIdx -1; i >= 0; i--) {
			TextBlock block = blockList.get(i);
			if (checkBlockNeedExpand(block))
				bestBlockRange.start = i;
			else 
				break;
		}
		
		int size = blockList.size();
		for (int i = bestBlockRange.goodBlockIdx; i < size; i++) {
			TextBlock block = blockList.get(i);
			
			if (checkBlockNeedExpand(block))
				bestBlockRange.end = i;
			else 
				break;
		}
		
		// then, consider other good text block
		int tmpIdx = bestBlockRange.start;
		for (int rangeIdx = blockRangeIdx-1; rangeIdx >= 0; rangeIdx--) {
			BlockRange range = goodBlockRangeList.get(rangeIdx);
			
			if (range.goodBlockIdx < tmpIdx - GoodBlockMaxInterval)
				break;
			
			if (range.goodBlockIdx < tmpIdx) {
				// compute range
				range.start = range.goodBlockIdx;
				range.end = range.goodBlockIdx;
				for (int i = range.goodBlockIdx -1; i >= 0; i--) {
					TextBlock block = blockList.get(i);
				
					if (checkBlockNeedExpand(block))
						range.start = i;
					else 
						break;
				}
				
				for (int i = range.goodBlockIdx; i < tmpIdx; i++) {
					TextBlock block = blockList.get(i);
					
					if (checkBlockNeedExpand(block))
						range.end = i;
					else 
						break;
				}
				
				tmpIdx = range.start;
			}
			
		}
		
		tmpIdx = bestBlockRange.end;
		for (int rangeIdx = blockRangeIdx + 1; rangeIdx < goodBlockRangeList.size(); rangeIdx++) {
			BlockRange range = goodBlockRangeList.get(rangeIdx);
			
			if (range.goodBlockIdx > tmpIdx + GoodBlockMaxInterval)
				break;
			
			if (range.goodBlockIdx > tmpIdx) {
				// compute range
				range.start = range.goodBlockIdx;
				range.end = range.goodBlockIdx;
				for (int i = range.goodBlockIdx -1; i > tmpIdx; i--) {
					TextBlock block = blockList.get(i);
					if (checkBlockNeedExpand(block))
						range.start = i;
					else 
						break;
				}
				
				for (int i = range.goodBlockIdx; i < blockList.size(); i++) {
					TextBlock block = blockList.get(i);
					if (checkBlockNeedExpand(block))
						range.end = i;
					else 
						break;
				}
				
				tmpIdx = range.end;
			}
		}
		
		return true;
	}
	
	public boolean checkBlockNeedExpand(TextBlock block) {
		return (block.tagNum == 0 || block.noAnchorChineseTextLen/block.tagNum > GoodBlockTagDenstiyLimit)
		  		&& block.noAnchorChineseTextLen > TextBlockSizeLimit;
	}
	
	public ArrayList<BlockRange> getGoodBlockRange() {
		BlockRange bestBlockRange = new BlockRange();
		bestBlockRange.goodBlockIdx = -1;
		bestBlockRange.goodBlock = null;
		
		int lastGoodBlockIdx = -1;
		int maxNonAnchorChineseTextLen = 0;
		ArrayList<BlockRange> goodBlockRangeList = new ArrayList<BlockRange>();
		for (int i = 0; i < blockList.size(); i++) {
			TextBlock block = blockList.get(i);
			
			boolean isGood = block.tagNum == 0 || block.noAnchorChineseTextLen/block.tagNum > GoodBlockTagDenstiyLimit;
			
			if (isGood && block.noAnchorChineseTextLen > TextSizeLimitExcepteAnchor) {					
				BlockRange range = new BlockRange();
				
				range.goodBlockIdx = i;
				range.goodBlock = block;
				goodBlockRangeList.add(range);
			}
			
			// if (block.noAnchorChineseTextLen > maxNonAnchorChineseTextLen && block.contTagNum < TagDenstiyLimit) {
			if (isGood && block.noAnchorChineseTextLen > maxNonAnchorChineseTextLen) {
				bestBlockRange.goodBlockIdx = i;
				bestBlockRange.goodBlock = block;
				bestBlockRange.isBest = true;
				maxNonAnchorChineseTextLen = block.noAnchorChineseTextLen;
			}
		}
		
		int rangeCount = goodBlockRangeList.size();
		if (rangeCount > 0) { 
			for (int i = 0; i < rangeCount; i++) {
				
				if (goodBlockRangeList.get(i).goodBlockIdx == bestBlockRange.goodBlockIdx)
					goodBlockRangeList.get(i).isBest = true;
			}
		} else {
			goodBlockRangeList.add(bestBlockRange);
		}
		
		return goodBlockRangeList;
	}

	public  void extractTextBlockList(NodeList nodeList, boolean isLink) {
		if (nodeList == null)
			return;
		
		for (Node node: nodeList.toNodeArray()) {
			if (node instanceof TextNode) {
				String text = stripHtmlPattern.matcher(node.getText()).replaceAll(" ");
				
				if (text.trim().length() > 0) {	
					if (prevIsText) {
						textBuf.append(" ");
						textBuf.append(text);	
						if (isLink) {
							currTextBlock.anchorTextLen += stripSpacePattern.matcher(text).replaceAll("").length();
						}
					} else {
						prevIsText = true;
						
						if (currTextBlock != null) {
							TextBlock lastBlock = currTextBlock;
							lastBlock.text = textBuf.toString();
							blockList.add(lastBlock);
						}
						
						// initial a new TextBlock
						currTextBlock = new TextBlock();
						textBuf.delete(0, textBuf.length());
						textBuf.append(text);
						if (isLink)
							currTextBlock.anchorTextLen += stripSpacePattern.matcher(text).replaceAll("").length();
						
					}
				}	
			}
			else if (node instanceof TagNode){
				
				String tagname = ((Tag)node).getTagName();
				
				if (node instanceof ScriptTag ||
						node instanceof StyleTag ||
						node instanceof SelectTag)  {
					continue;
				}
				
				if (!"FONT".equals(tagname)) {
					currTagNum++; 
				}
				
				boolean isLinkNode = false;
				boolean hasBlockTag = false;
				
				
				Matcher blockTagNameMatcher = blockTagNamePattern
						.matcher(tagname);
				if (blockTagNameMatcher.matches()) {

					if (currTextBlock != null) {
						currTextBlock.contTagNum++;

					}

					if (prevIsText) {
						currTextBlock.tagNum = currTagNum + 1;
						currTagNum = 1;
					} else {
						currTagNum = 1;
					}

					prevIsText = false;
					hasBlockTag = true;
				} else if ("A".equals(tagname)) {
					isLinkNode = true;
				}
				

				extractTextBlockList(node.getChildren(), isLink || isLinkNode);
				
				if (hasBlockTag) {
					if (currTextBlock != null)
						currTextBlock.contTagNum++;
					
					if (prevIsText) {
						currTextBlock.tagNum = currTagNum + 1;
						currTagNum = 1;
					} else {
						currTagNum = 1;
					}
					prevIsText = false;
				}
			}
		}
		/*
		if (currTextBlock != null) {
			TextBlock lastBlock = currTextBlock;
			lastBlock.text = textBuf.toString();
			blockList.add(lastBlock);
		}*/
	}
	
	public  NodeList getDomTree(Page page) {
		Parser parser = Parser.createParser(page.rawHtml, "UTF-8");

		NodeList nodeList = null;
		try {
			//parser.setURL(page.url);
			nodeList = parser.parse(null);
		} catch (ParserException e) {
			e.printStackTrace();
			return null;
		}
		
		return nodeList;
	}
}
