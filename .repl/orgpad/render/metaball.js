// Compiled by ClojureScript 0.0-2814 {}
goog.provide('orgpad.render.metaball');
goog.require('cljs.core');
orgpad.render.metaball.norm_squared = (function norm_squared(pt1,pt2){
return (((pt1.call(null,(0)) - pt2.call(null,(0))) * (pt1.call(null,(0)) - pt2.call(null,(0)))) + ((pt1.call(null,(1)) - pt2.call(null,(1))) * (pt1.call(null,(1)) - pt2.call(null,(1)))));
});
orgpad.render.metaball.blob_fcion = (function blob_fcion(centre,pt,size){

return (size / orgpad.render.metaball.norm_squared.call(null,pt,centre));
});
orgpad.render.metaball.total_blob_fcion = (function total_blob_fcion(pt,blobs){
var temp = cljs.core.peek.call(null,blobs);
if(cljs.core.truth_(cljs.core.peek.call(null,blobs))){
return cljs.core.conj.call(null,total_blob_fcion.call(null,pt,cljs.core.pop.call(null,blobs)),orgpad.render.metaball.blob_fcion.call(null,cljs.core.get.call(null,temp,new cljs.core.Keyword(null,"centre","centre",-948091970)),pt,cljs.core.get.call(null,temp,new cljs.core.Keyword(null,"size","size",1098693007))));
} else {
return cljs.core.PersistentVector.EMPTY;
}
});
orgpad.render.metaball.resize_blobs = (function resize_blobs(size,blobs){
var temp = cljs.core.peek.call(null,blobs);
if(cljs.core.truth_(cljs.core.peek.call(null,blobs))){
return cljs.core.conj.call(null,resize_blobs.call(null,size,cljs.core.pop.call(null,blobs)),cljs.core.assoc.call(null,temp,new cljs.core.Keyword(null,"size","size",1098693007),(size * cljs.core.get.call(null,temp,new cljs.core.Keyword(null,"size","size",1098693007)))));
} else {
return cljs.core.PersistentVector.EMPTY;
}
});
orgpad.render.metaball.shift = (function shift(centre1,centre2){

var norm = Math.sqrt(orgpad.render.metaball.norm_squared.call(null,centre1,centre2));
return (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[(- (((20) * (centre1.call(null,(0)) - centre2.call(null,(0)))) / norm)),(((20) * (centre1.call(null,(1)) - centre2.call(null,(1)))) / norm)],null));
});
orgpad.render.metaball.get_coef = (function get_coef(i,j,blobs){

var correction = orgpad.render.metaball.shift.call(null,cljs.core.get.call(null,blobs.call(null,i),new cljs.core.Keyword(null,"centre","centre",-948091970)),cljs.core.get.call(null,blobs.call(null,j),new cljs.core.Keyword(null,"centre","centre",-948091970)));
var pt = (new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[(((cljs.core.first.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,i))) + cljs.core.first.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,j)))) / (2)) + correction.call(null,(0))),(((cljs.core.last.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,i))) + cljs.core.last.call(null,new cljs.core.Keyword(null,"centre","centre",-948091970).cljs$core$IFn$_invoke$arity$1(blobs.call(null,j)))) / (2)) + correction.call(null,(1)))],null));
return ((1) / cljs.core.reduce.call(null,cljs.core._PLUS_,orgpad.render.metaball.total_blob_fcion.call(null,pt,blobs)));
});
/**
* @param {...*} var_args
*/
orgpad.render.metaball.calc_blob = (function() { 
var calc_blob__delegate = function (blob_list){

var blobs = cljs.core.into.call(null,cljs.core.PersistentVector.EMPTY,blob_list);
var coef = cljs.core.reduce.call(null,cljs.core.max,(function (){var iter__6282__auto__ = ((function (blobs){
return (function iter__12556(s__12557){
return (new cljs.core.LazySeq(null,((function (blobs){
return (function (){
var s__12557__$1 = s__12557;
while(true){
var temp__4126__auto__ = cljs.core.seq.call(null,s__12557__$1);
if(temp__4126__auto__){
var xs__4624__auto__ = temp__4126__auto__;
var i = cljs.core.first.call(null,xs__4624__auto__);
var iterys__6278__auto__ = ((function (s__12557__$1,i,xs__4624__auto__,temp__4126__auto__,blobs){
return (function iter__12558(s__12559){
return (new cljs.core.LazySeq(null,((function (s__12557__$1,i,xs__4624__auto__,temp__4126__auto__,blobs){
return (function (){
var s__12559__$1 = s__12559;
while(true){
var temp__4126__auto____$1 = cljs.core.seq.call(null,s__12559__$1);
if(temp__4126__auto____$1){
var s__12559__$2 = temp__4126__auto____$1;
if(cljs.core.chunked_seq_QMARK_.call(null,s__12559__$2)){
var c__6280__auto__ = cljs.core.chunk_first.call(null,s__12559__$2);
var size__6281__auto__ = cljs.core.count.call(null,c__6280__auto__);
var b__12561 = cljs.core.chunk_buffer.call(null,size__6281__auto__);
if((function (){var i__12560 = (0);
while(true){
if((i__12560 < size__6281__auto__)){
var j = cljs.core._nth.call(null,c__6280__auto__,i__12560);
if((i > j)){
cljs.core.chunk_append.call(null,b__12561,orgpad.render.metaball.get_coef.call(null,i,j,blobs));

var G__12562 = (i__12560 + (1));
i__12560 = G__12562;
continue;
} else {
var G__12563 = (i__12560 + (1));
i__12560 = G__12563;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12561),iter__12558.call(null,cljs.core.chunk_rest.call(null,s__12559__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__12561),null);
}
} else {
var j = cljs.core.first.call(null,s__12559__$2);
if((i > j)){
return cljs.core.cons.call(null,orgpad.render.metaball.get_coef.call(null,i,j,blobs),iter__12558.call(null,cljs.core.rest.call(null,s__12559__$2)));
} else {
var G__12564 = cljs.core.rest.call(null,s__12559__$2);
s__12559__$1 = G__12564;
continue;
}
}
} else {
return null;
}
break;
}
});})(s__12557__$1,i,xs__4624__auto__,temp__4126__auto__,blobs))
,null,null));
});})(s__12557__$1,i,xs__4624__auto__,temp__4126__auto__,blobs))
;
var fs__6279__auto__ = cljs.core.seq.call(null,iterys__6278__auto__.call(null,cljs.core.range.call(null,(0),cljs.core.count.call(null,blobs))));
if(fs__6279__auto__){
return cljs.core.concat.call(null,fs__6279__auto__,iter__12556.call(null,cljs.core.rest.call(null,s__12557__$1)));
} else {
var G__12565 = cljs.core.rest.call(null,s__12557__$1);
s__12557__$1 = G__12565;
continue;
}
} else {
return null;
}
break;
}
});})(blobs))
,null,null));
});})(blobs))
;
return iter__6282__auto__.call(null,cljs.core.range.call(null,(0),cljs.core.count.call(null,blobs)));
})());
cljs.core.print.call(null,coef,blobs);

return orgpad.render.metaball.resize_blobs.call(null,(function (){var x__5843__auto__ = (1);
var y__5844__auto__ = coef;
return ((x__5843__auto__ > y__5844__auto__) ? x__5843__auto__ : y__5844__auto__);
})(),blobs);
};
var calc_blob = function (var_args){
var blob_list = null;
if (arguments.length > 0) {
var G__12566__i = 0, G__12566__a = new Array(arguments.length -  0);
while (G__12566__i < G__12566__a.length) {G__12566__a[G__12566__i] = arguments[G__12566__i + 0]; ++G__12566__i;}
  blob_list = new cljs.core.IndexedSeq(G__12566__a,0);
} 
return calc_blob__delegate.call(this,blob_list);};
calc_blob.cljs$lang$maxFixedArity = 0;
calc_blob.cljs$lang$applyTo = (function (arglist__12567){
var blob_list = cljs.core.seq(arglist__12567);
return calc_blob__delegate(blob_list);
});
calc_blob.cljs$core$IFn$_invoke$arity$variadic = calc_blob__delegate;
return calc_blob;
})()
;
