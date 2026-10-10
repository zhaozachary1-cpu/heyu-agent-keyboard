package ai.heyu.agent;

/** A title or UI node/window ID is not a WhatsApp contact ID. Never auto-restore by name. */
final class ConversationPolicy {
 // Even "Message yourself" is not account-scoped; WhatsApp supports account switching.
 // This adapter has no verified stable peer/account ID; new surfaces must be isolated.
 static String proof(String status){return "";}
 static boolean sameEditorRestart(boolean restarting,boolean visible,String previousPackage,String nextPackage){return restarting&&visible&&!previousPackage.isEmpty()&&previousPackage.equals(nextPackage);}
 static boolean sameVisible(String title,int window,String nextTitle,int nextWindow){return !title.codePoints().allMatch(Character::isWhitespace)&&title.equals(nextTitle)&&window==nextWindow;}
 static boolean mayRestore(String proof,String savedProof){return !proof.isEmpty()&&proof.equals(savedProof);}
 static boolean accepts(ConversationState owner,int ticket){return owner.alive&&owner.epoch==ticket;}
 static boolean acceptsWork(ConversationState owner,int ticket){return owner.alive&&owner.workEpoch==ticket;}
}
