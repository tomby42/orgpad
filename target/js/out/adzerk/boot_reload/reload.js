// Compiled by ClojureScript 0.0-2814 {}
goog.provide('adzerk.boot_reload.reload');
goog.require('cljs.core');
goog.require('goog.net.jsloader');
goog.require('goog.async.DeferredList');
goog.require('goog.Uri');
goog.require('clojure.string');
adzerk.boot_reload.reload.page_uri = (new goog.Uri(window.location.href));
adzerk.boot_reload.reload.ends_with_QMARK_ = (function ends_with_QMARK_(s,pat){
return cljs.core._EQ_.call(null,pat,cljs.core.subs.call(null,s,(cljs.core.count.call(null,s) - cljs.core.count.call(null,pat))));
});
adzerk.boot_reload.reload.reload_page_BANG_ = (function reload_page_BANG_(){
return window.location.reload();
});
adzerk.boot_reload.reload.changed_href_QMARK_ = (function changed_href_QMARK_(href_or_uri,changed){
if(cljs.core.truth_(href_or_uri)){
var uri = (new goog.Uri(href_or_uri));
var path = adzerk.boot_reload.reload.page_uri.resolve(uri).getPath();
if(cljs.core.truth_(cljs.core.not_empty.call(null,cljs.core.filter.call(null,((function (uri,path){
return (function (p1__5359_SHARP_){
return adzerk.boot_reload.reload.ends_with_QMARK_.call(null,p1__5359_SHARP_,path);
});})(uri,path))
,changed)))){
return uri;
} else {
return null;
}
} else {
return null;
}
});
adzerk.boot_reload.reload.reload_css = (function reload_css(changed){
var sheets = document.styleSheets;
var seq__5364 = cljs.core.seq.call(null,cljs.core.range.call(null,(0),sheets.length));
var chunk__5365 = null;
var count__5366 = (0);
var i__5367 = (0);
while(true){
if((i__5367 < count__5366)){
var s = cljs.core._nth.call(null,chunk__5365,i__5367);
var temp__4126__auto___5368 = (sheets[s]);
if(cljs.core.truth_(temp__4126__auto___5368)){
var sheet_5369 = temp__4126__auto___5368;
var temp__4126__auto___5370__$1 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,sheet_5369.href,changed);
if(cljs.core.truth_(temp__4126__auto___5370__$1)){
var href_uri_5371 = temp__4126__auto___5370__$1;
sheet_5369.ownerNode.href = href_uri_5371.makeUnique().toString();
} else {
}
} else {
}

var G__5372 = seq__5364;
var G__5373 = chunk__5365;
var G__5374 = count__5366;
var G__5375 = (i__5367 + (1));
seq__5364 = G__5372;
chunk__5365 = G__5373;
count__5366 = G__5374;
i__5367 = G__5375;
continue;
} else {
var temp__4126__auto__ = cljs.core.seq.call(null,seq__5364);
if(temp__4126__auto__){
var seq__5364__$1 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__5364__$1)){
var c__4369__auto__ = cljs.core.chunk_first.call(null,seq__5364__$1);
var G__5376 = cljs.core.chunk_rest.call(null,seq__5364__$1);
var G__5377 = c__4369__auto__;
var G__5378 = cljs.core.count.call(null,c__4369__auto__);
var G__5379 = (0);
seq__5364 = G__5376;
chunk__5365 = G__5377;
count__5366 = G__5378;
i__5367 = G__5379;
continue;
} else {
var s = cljs.core.first.call(null,seq__5364__$1);
var temp__4126__auto___5380__$1 = (sheets[s]);
if(cljs.core.truth_(temp__4126__auto___5380__$1)){
var sheet_5381 = temp__4126__auto___5380__$1;
var temp__4126__auto___5382__$2 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,sheet_5381.href,changed);
if(cljs.core.truth_(temp__4126__auto___5382__$2)){
var href_uri_5383 = temp__4126__auto___5382__$2;
sheet_5381.ownerNode.href = href_uri_5383.makeUnique().toString();
} else {
}
} else {
}

var G__5384 = cljs.core.next.call(null,seq__5364__$1);
var G__5385 = null;
var G__5386 = (0);
var G__5387 = (0);
seq__5364 = G__5384;
chunk__5365 = G__5385;
count__5366 = G__5386;
i__5367 = G__5387;
continue;
}
} else {
return null;
}
}
break;
}
});
adzerk.boot_reload.reload.reload_img = (function reload_img(changed){
var images = document.images;
var seq__5392 = cljs.core.seq.call(null,cljs.core.range.call(null,(0),images.length));
var chunk__5393 = null;
var count__5394 = (0);
var i__5395 = (0);
while(true){
if((i__5395 < count__5394)){
var s = cljs.core._nth.call(null,chunk__5393,i__5395);
var temp__4126__auto___5396 = (images[s]);
if(cljs.core.truth_(temp__4126__auto___5396)){
var image_5397 = temp__4126__auto___5396;
var temp__4126__auto___5398__$1 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,image_5397.src,changed);
if(cljs.core.truth_(temp__4126__auto___5398__$1)){
var href_uri_5399 = temp__4126__auto___5398__$1;
image_5397.src = href_uri_5399.makeUnique().toString();
} else {
}
} else {
}

var G__5400 = seq__5392;
var G__5401 = chunk__5393;
var G__5402 = count__5394;
var G__5403 = (i__5395 + (1));
seq__5392 = G__5400;
chunk__5393 = G__5401;
count__5394 = G__5402;
i__5395 = G__5403;
continue;
} else {
var temp__4126__auto__ = cljs.core.seq.call(null,seq__5392);
if(temp__4126__auto__){
var seq__5392__$1 = temp__4126__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__5392__$1)){
var c__4369__auto__ = cljs.core.chunk_first.call(null,seq__5392__$1);
var G__5404 = cljs.core.chunk_rest.call(null,seq__5392__$1);
var G__5405 = c__4369__auto__;
var G__5406 = cljs.core.count.call(null,c__4369__auto__);
var G__5407 = (0);
seq__5392 = G__5404;
chunk__5393 = G__5405;
count__5394 = G__5406;
i__5395 = G__5407;
continue;
} else {
var s = cljs.core.first.call(null,seq__5392__$1);
var temp__4126__auto___5408__$1 = (images[s]);
if(cljs.core.truth_(temp__4126__auto___5408__$1)){
var image_5409 = temp__4126__auto___5408__$1;
var temp__4126__auto___5410__$2 = adzerk.boot_reload.reload.changed_href_QMARK_.call(null,image_5409.src,changed);
if(cljs.core.truth_(temp__4126__auto___5410__$2)){
var href_uri_5411 = temp__4126__auto___5410__$2;
image_5409.src = href_uri_5411.makeUnique().toString();
} else {
}
} else {
}

var G__5412 = cljs.core.next.call(null,seq__5392__$1);
var G__5413 = null;
var G__5414 = (0);
var G__5415 = (0);
seq__5392 = G__5412;
chunk__5393 = G__5413;
count__5394 = G__5414;
i__5395 = G__5415;
continue;
}
} else {
return null;
}
}
break;
}
});
adzerk.boot_reload.reload.reload_js = (function reload_js(changed,p__5418){
var map__5420 = p__5418;
var map__5420__$1 = ((cljs.core.seq_QMARK_.call(null,map__5420))?cljs.core.apply.call(null,cljs.core.hash_map,map__5420):map__5420);
var on_jsload = cljs.core.get.call(null,map__5420__$1,new cljs.core.Keyword(null,"on-jsload","on-jsload",-395756602),cljs.core.identity);
var js_files = cljs.core.filter.call(null,((function (map__5420,map__5420__$1,on_jsload){
return (function (p1__5416_SHARP_){
return adzerk.boot_reload.reload.ends_with_QMARK_.call(null,p1__5416_SHARP_,".js");
});})(map__5420,map__5420__$1,on_jsload))
,changed);
if(cljs.core.seq.call(null,js_files)){
goog.async.DeferredList.gatherResults(cljs.core.clj__GT_js.call(null,cljs.core.map.call(null,((function (js_files,map__5420,map__5420__$1,on_jsload){
return (function (p1__5417_SHARP_){
return goog.net.jsloader.load(goog.Uri.parse(p1__5417_SHARP_).makeUnique());
});})(js_files,map__5420,map__5420__$1,on_jsload))
,js_files))).addCallbacks(((function (js_files,map__5420,map__5420__$1,on_jsload){
return (function() { 
var G__5421__delegate = function (_){
return on_jsload.call(null);
};
var G__5421 = function (var_args){
var _ = null;
if (arguments.length > 0) {
var G__5422__i = 0, G__5422__a = new Array(arguments.length -  0);
while (G__5422__i < G__5422__a.length) {G__5422__a[G__5422__i] = arguments[G__5422__i + 0]; ++G__5422__i;}
  _ = new cljs.core.IndexedSeq(G__5422__a,0);
} 
return G__5421__delegate.call(this,_);};
G__5421.cljs$lang$maxFixedArity = 0;
G__5421.cljs$lang$applyTo = (function (arglist__5423){
var _ = cljs.core.seq(arglist__5423);
return G__5421__delegate(_);
});
G__5421.cljs$core$IFn$_invoke$arity$variadic = G__5421__delegate;
return G__5421;
})()
;})(js_files,map__5420,map__5420__$1,on_jsload))
,((function (js_files,map__5420,map__5420__$1,on_jsload){
return (function (e){
return console.error("Load failed:",e.message);
});})(js_files,map__5420,map__5420__$1,on_jsload))
);

if(cljs.core.truth_((window["jQuery"]))){
return jQuery(document).trigger("page-load");
} else {
return null;
}
} else {
return null;
}
});
adzerk.boot_reload.reload.reload_html = (function reload_html(changed){
var page_path = adzerk.boot_reload.reload.page_uri.getPath();
var html_path = ((adzerk.boot_reload.reload.ends_with_QMARK_.call(null,page_path,"/"))?[cljs.core.str(page_path),cljs.core.str("index.html")].join(''):page_path);
if(cljs.core.truth_(adzerk.boot_reload.reload.changed_href_QMARK_.call(null,html_path,changed))){
return adzerk.boot_reload.reload.reload_page_BANG_.call(null);
} else {
return null;
}
});
adzerk.boot_reload.reload.group_log = (function group_log(title,things_to_log){
console.groupCollapsed(title);

var seq__5428_5432 = cljs.core.seq.call(null,things_to_log);
var chunk__5429_5433 = null;
var count__5430_5434 = (0);
var i__5431_5435 = (0);
while(true){
if((i__5431_5435 < count__5430_5434)){
var t_5436 = cljs.core._nth.call(null,chunk__5429_5433,i__5431_5435);
console.log(t_5436);

var G__5437 = seq__5428_5432;
var G__5438 = chunk__5429_5433;
var G__5439 = count__5430_5434;
var G__5440 = (i__5431_5435 + (1));
seq__5428_5432 = G__5437;
chunk__5429_5433 = G__5438;
count__5430_5434 = G__5439;
i__5431_5435 = G__5440;
continue;
} else {
var temp__4126__auto___5441 = cljs.core.seq.call(null,seq__5428_5432);
if(temp__4126__auto___5441){
var seq__5428_5442__$1 = temp__4126__auto___5441;
if(cljs.core.chunked_seq_QMARK_.call(null,seq__5428_5442__$1)){
var c__4369__auto___5443 = cljs.core.chunk_first.call(null,seq__5428_5442__$1);
var G__5444 = cljs.core.chunk_rest.call(null,seq__5428_5442__$1);
var G__5445 = c__4369__auto___5443;
var G__5446 = cljs.core.count.call(null,c__4369__auto___5443);
var G__5447 = (0);
seq__5428_5432 = G__5444;
chunk__5429_5433 = G__5445;
count__5430_5434 = G__5446;
i__5431_5435 = G__5447;
continue;
} else {
var t_5448 = cljs.core.first.call(null,seq__5428_5442__$1);
console.log(t_5448);

var G__5449 = cljs.core.next.call(null,seq__5428_5442__$1);
var G__5450 = null;
var G__5451 = (0);
var G__5452 = (0);
seq__5428_5432 = G__5449;
chunk__5429_5433 = G__5450;
count__5430_5434 = G__5451;
i__5431_5435 = G__5452;
continue;
}
} else {
}
}
break;
}

return console.groupEnd();
});
adzerk.boot_reload.reload.reload = (function reload(opts,changed){
adzerk.boot_reload.reload.group_log.call(null,"Reload",changed);

var G__5454 = changed;
adzerk.boot_reload.reload.reload_js.call(null,G__5454,opts);

adzerk.boot_reload.reload.reload_html.call(null,G__5454);

adzerk.boot_reload.reload.reload_css.call(null,G__5454);

adzerk.boot_reload.reload.reload_img.call(null,G__5454);

return G__5454;
});
