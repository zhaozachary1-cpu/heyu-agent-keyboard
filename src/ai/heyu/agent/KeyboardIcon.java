package ai.heyu.agent;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small original 24dp outline icons; no private Gboard assets. */
final class KeyboardIcon extends Drawable {
 private final String name; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final int color;
 KeyboardIcon(String name,int color){this.name=name;this.color=color;}
 @Override public void draw(Canvas canvas){
  canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
  p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
  Path path=new Path();
  switch(name){
   case "shift": case "caps":
    path.moveTo(12,4);path.lineTo(3,13);path.lineTo(8,13);path.lineTo(8,20);path.lineTo(16,20);path.lineTo(16,13);path.lineTo(21,13);path.close();canvas.drawPath(path,p);
    if(name.equals("caps"))canvas.drawLine(8,23,16,23,p);break;
   case "delete":
    path.moveTo(9,5);path.lineTo(21,5);path.lineTo(21,19);path.lineTo(9,19);path.lineTo(2,12);path.close();canvas.drawPath(path,p);canvas.drawLine(12,9,17,15,p);canvas.drawLine(17,9,12,15,p);break;
   case "enter":
    path.moveTo(20,6);path.lineTo(20,14);path.lineTo(5,14);path.moveTo(9,10);path.lineTo(5,14);path.lineTo(9,18);canvas.drawPath(path,p);break;
   case "done":path.moveTo(5,12);path.lineTo(10,17);path.lineTo(20,6);canvas.drawPath(path,p);break;
   case "back":path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);canvas.drawPath(path,p);break;
   case "down":path.moveTo(6,9);path.lineTo(12,15);path.lineTo(18,9);canvas.drawPath(path,p);break;
   case "history":
    canvas.drawArc(4,4,20,20,-90,285,false,p);path.moveTo(4,4);path.lineTo(4,9);path.lineTo(9,9);canvas.drawPath(path,p);canvas.drawLine(12,7,12,12,p);canvas.drawLine(12,12,16,14,p);break;
   case "more":p.setStyle(Paint.Style.FILL);canvas.drawCircle(5,12,1.8f,p);canvas.drawCircle(12,12,1.8f,p);canvas.drawCircle(19,12,1.8f,p);break;
   case "keyboard":canvas.drawRoundRect(2,5,22,19,3,3,p);p.setStyle(Paint.Style.FILL);for(int y=8;y<=11;y+=3)for(int x=6;x<=18;x+=4)canvas.drawCircle(x,y,.8f,p);canvas.drawRoundRect(7,15,17,16,1,1,p);break;
   case "paste":canvas.drawRoundRect(5,5,20,22,2,2,p);canvas.drawRoundRect(9,2,16,7,1,1,p);canvas.drawLine(9,12,16,12,p);canvas.drawLine(9,16,16,16,p);break;
   case "language":canvas.drawCircle(12,12,9,p);canvas.drawOval(8,3,16,21,p);canvas.drawLine(3,12,21,12,p);canvas.drawLine(5,7,19,7,p);canvas.drawLine(5,17,19,17,p);break;
   case "spark":
    path.moveTo(12,3);path.quadTo(13,10,20,12);path.quadTo(13,13,12,21);path.quadTo(11,14,4,12);path.quadTo(11,11,12,3);canvas.drawPath(path,p);break;
   case "edit":path.moveTo(5,15);path.lineTo(16,4);path.lineTo(20,8);path.lineTo(9,19);path.lineTo(4,20);path.close();canvas.drawPath(path,p);canvas.drawLine(14,6,18,10,p);break;
   default:canvas.drawCircle(12,12,7,p);
  }
  canvas.restore();
 }
 @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
 @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
