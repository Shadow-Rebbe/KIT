package com.kit.prototype

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var store: KitStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = KitStore(this)
        val deckId=intent.getStringExtra("openDeckId")
        val cardId=intent.getStringExtra("openCardId")
        val initial:Screen = if(deckId!=null && cardId!=null && store.deck(deckId)!=null && store.card(cardId)!=null) Screen.Deal(deckId,cardId) else Screen.Decks
        setContent { KitApp(store, initial) }
    }
}

private sealed class Screen {
    data object Decks: Screen()
    data object Cards: Screen()
    data object Settings: Screen()
    data class DeckMenu(val deckId:String): Screen()
    data class EditDeck(val deckId:String): Screen()
    data class DeckCards(val deckId:String): Screen()
    data class DeckSettings(val deckId:String): Screen()
    data class Deal(val deckId:String, val cardId:String): Screen()
    data class EditCard(val cardId:String): Screen()
}

@Composable
fun KitApp(store: KitStore, initialScreen: Screen = Screen.Decks) {
    var epoch by remember { mutableIntStateOf(0) }
    val systemDark = (LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val dark = when(store.appearance){ Appearance.SYSTEM -> systemDark; Appearance.LIGHT -> false; Appearance.DARK -> true }
    var screen by remember { mutableStateOf<Screen>(initialScreen) }
    BackHandler(enabled = screen !is Screen.Decks && screen !is Screen.Cards && screen !is Screen.Settings && screen !is Screen.EditCard) {
        screen = when(val s=screen){
            is Screen.DeckMenu -> Screen.Decks
            is Screen.EditDeck -> Screen.DeckMenu(s.deckId)
            is Screen.DeckCards -> Screen.EditDeck(s.deckId)
            is Screen.DeckSettings -> Screen.EditDeck(s.deckId)
            is Screen.Deal -> Screen.DeckMenu(s.deckId)
            else -> Screen.Decks
        }
    }
    val bg = if(dark) Color.Black else Color.White
    val fg = if(dark) Color.White else Color.Black
    val muted = if(dark) Color(0xFFAAAAAA) else Color(0xFF666666)
    val scheme = if(dark) darkColorScheme(background=Color.Black,surface=Color.Black,onBackground=Color.White,onSurface=Color.White,primary=Color.White,onPrimary=Color.Black) else lightColorScheme(background=Color.White,surface=Color.White,onBackground=Color.Black,onSurface=Color.Black,primary=Color.Black,onPrimary=Color.White)
    fun changed(){ store.save(); epoch++ }
    MaterialTheme(colorScheme=scheme) {
        Scaffold(containerColor=bg, bottomBar={ if(screen is Screen.Decks || screen is Screen.Cards || screen is Screen.Settings) BottomNav(screen, {screen=it}, fg, muted) }) { pad ->
            Box(Modifier.fillMaxSize().background(bg).padding(pad)) {
                when(val s=screen) {
                    Screen.Decks -> DecksHome(store, fg, muted, {screen=Screen.DeckMenu(it)}, { val d=store.newDeck(); changed(); screen=Screen.DeckSettings(d.id) })
                    Screen.Cards -> CardsHome(store, fg, muted, {screen=Screen.EditCard(it)}, { val c=store.newCard(); changed(); screen=Screen.EditCard(c.id) })
                    Screen.Settings -> SettingsScreen(store, fg, muted, ::changed)
                    is Screen.DeckMenu -> DeckMenuScreen(store, s.deckId, fg, muted, {screen=Screen.Decks}, {screen=Screen.EditDeck(s.deckId)}, {
                        val deck=store.deck(s.deckId); val card=deck?.let { selectCard(store,it,null) }; if(card!=null) screen=Screen.Deal(s.deckId,card.id)
                    })
                    is Screen.EditDeck -> EditDeckFork(store,s.deckId,fg,muted,{screen=Screen.DeckMenu(s.deckId)},{screen=Screen.DeckCards(s.deckId)},{screen=Screen.DeckSettings(s.deckId)})
                    is Screen.DeckCards -> DeckCardsScreen(store,s.deckId,fg,muted,{screen=Screen.EditDeck(s.deckId)},{screen=Screen.EditCard(it)}, {val c=store.newCard(); store.deck(s.deckId)?.cardIds?.add(c.id);changed();screen=Screen.EditCard(c.id)})
                    is Screen.DeckSettings -> DeckSettingsScreen(store,s.deckId,fg,muted,{changed();screen=Screen.EditDeck(s.deckId)},::changed)
                    is Screen.Deal -> DealScreen(store,s.deckId,s.cardId,fg,muted,{screen=Screen.DeckMenu(s.deckId)},{
                        val d=store.deck(s.deckId); val c=d?.let{selectCard(store,it,s.cardId)}; if(c!=null) screen=Screen.Deal(s.deckId,c.id)
                    }, {screen=Screen.EditCard(s.cardId)}, ::changed, {screen=Screen.DeckMenu(s.deckId)})
                    is Screen.EditCard -> EditCardScreen(store,s.cardId,fg,muted,{changed();screen=Screen.Cards},::changed)
                }
            }
        }
    }
}

@Composable private fun BottomNav(screen:Screen,onSelect:(Screen)->Unit,fg:Color,muted:Color){
    NavigationBar(containerColor=if(fg==Color.White) Color.Black else Color.White, tonalElevation=0.dp){
        listOf(Triple("▣","Decks",Screen.Decks),Triple("👤","Cards",Screen.Cards),Triple("⚙","Settings",Screen.Settings)).forEach{(icon,label,target)->
            val selected=screen::class==target::class
            NavigationBarItem(selected=selected,onClick={onSelect(target)},icon={Text(icon,fontSize=20.sp)},label={Text(label)},colors=NavigationBarItemDefaults.colors(selectedIconColor=fg,selectedTextColor=fg,unselectedIconColor=muted,unselectedTextColor=muted,indicatorColor=Color.Transparent))
        }
    }
}

@Composable private fun Header(title:String,onBack:(()->Unit)?=null,fg:Color){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
        if(onBack!=null) Text("‹",fontSize=40.sp,modifier=Modifier.clickable{onBack()}.padding(end=10.dp),color=fg)
        Text(title,fontSize=28.sp,fontWeight=FontWeight.SemiBold,color=fg,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable private fun DecksHome(store:KitStore,fg:Color,muted:Color,onDeck:(String)->Unit,onNew:()->Unit){
    Column(Modifier.fillMaxSize()){
        Header("KIT",fg=fg)
        if(store.decks.isEmpty()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){ Column(horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.size(72.dp).border(1.dp,fg,CircleShape).clickable{onNew()},contentAlignment=Alignment.Center){Text("+",fontSize=36.sp,color=fg)}
            Spacer(Modifier.height(12.dp));Text("New deck",color=fg)
        }} else LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
            items(store.decks){d->DeckBack(d,fg,muted){onDeck(d.id)}}
            item{TextButton(onClick=onNew){Text("+ New deck",color=fg)}}
        }
    }
}

@Composable private fun DeckBack(deck:KitDeck,fg:Color,muted:Color,onClick:()->Unit){
    val dark = fg==Color.White
    val fill=if(dark) Color(0xFF111111) else Color(0xFFF7F7F7)
    Column(Modifier.fillMaxWidth().clickable{onClick()}){
        Box(Modifier.fillMaxWidth().aspectRatio(1.58f).background(fill,RoundedCornerShape(22.dp)).border(1.dp,if(dark) Color(0xFF333333) else Color(0xFFDDDDDD),RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){
            deck.imageUri?.let { StoredImage(it, Modifier.matchParentSize(), ContentScale.Crop) }
            Box(Modifier.matchParentSize().background(if(deck.imageUri!=null) Color.Black.copy(alpha=.36f) else Color.Transparent, RoundedCornerShape(22.dp)))
            Column(horizontalAlignment=Alignment.CenterHorizontally){ Text(deck.title,fontSize=25.sp,fontWeight=FontWeight.Medium,color=if(deck.imageUri!=null)Color.White else fg); Spacer(Modifier.height(8.dp)); Text("KIT",fontSize=12.sp,letterSpacing=4.sp,color=if(deck.imageUri!=null)Color.White.copy(alpha=.75f) else muted) }
        }
    }
}

@Composable private fun DeckMenuScreen(store:KitStore,id:String,fg:Color,muted:Color,onBack:()->Unit,onEdit:()->Unit,onDeal:()->Unit){
    val d=store.deck(id)?:return; Column(Modifier.fillMaxSize()){
        Header(d.title,onBack,fg)
        Column(Modifier.padding(24.dp)){ if(d.description.isNotBlank()){Text(d.description,color=fg,fontSize=17.sp);Spacer(Modifier.height(12.dp))};Text("${d.cardIds.size} cards",color=muted);Spacer(Modifier.height(32.dp));PrimaryButton("Deal a card",fg,onDeal);Spacer(Modifier.height(12.dp));OutlineButton("Edit deck",fg,onEdit) }
    }
}

@Composable private fun EditDeckFork(store:KitStore,id:String,fg:Color,muted:Color,onBack:()->Unit,onCards:()->Unit,onSettings:()->Unit){
    val d=store.deck(id)?:return;Column(Modifier.fillMaxSize()){Header("Edit ${d.title}",onBack,fg);Column(Modifier.padding(24.dp)){MenuRow("View & edit cards","${d.cardIds.size} cards",fg,muted,onCards);Divider();MenuRow("Deck settings","Title, description, image, cadence",fg,muted,onSettings)}}
}

@Composable private fun DeckCardsScreen(store:KitStore,id:String,fg:Color,muted:Color,onBack:()->Unit,onCard:(String)->Unit,onNew:()->Unit){
    val d=store.deck(id)?:return;Column(Modifier.fillMaxSize()){Header("Cards",onBack,fg);LazyColumn(contentPadding=PaddingValues(20.dp)){items(d.cardIds.mapNotNull(store::card)){c->MenuRow(c.name,c.items.firstOrNull()?.text?:"",fg,muted){onCard(c.id)};Divider()};item{TextButton(onClick=onNew){Text("+ Add card",color=fg)}}}}
}

@Composable private fun DeckSettingsScreen(store:KitStore,id:String,fg:Color,muted:Color,onBack:()->Unit,onChanged:()->Unit){
    val d=store.deck(id)?:return;var title by remember(d.title){mutableStateOf(d.title)};var desc by remember(d.description){mutableStateOf(d.description)};var cadence by remember(d.cadenceDays){mutableStateOf(d.cadenceDays.toString())};val ctx=LocalContext.current;val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{ctx.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};d.imageUri=u.toString();onChanged()}}
    Column(Modifier.fillMaxSize()){Header("Deck settings",onBack,fg);LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Field("Title",title,fg){title=it;d.title=it;onChanged()}}
        item{Field("Description",desc,fg,true){desc=it;d.description=it;onChanged()}}
        item{Text("Image",color=muted,fontSize=13.sp);Spacer(Modifier.height(6.dp));OutlineButton(if(d.imageUri==null)"Choose image" else "Change image",fg){picker.launch(arrayOf("image/*"))}}
        item{Field("Cadence (days)",cadence,fg){cadence=it;it.toIntOrNull()?.let{v->d.cadenceDays=v.coerceAtLeast(1);onChanged()}}}
    }}
}

@Composable private fun CardsHome(store:KitStore,fg:Color,muted:Color,onCard:(String)->Unit,onNew:()->Unit){
    Column(Modifier.fillMaxSize()){Header("Cards",fg=fg);LazyColumn(contentPadding=PaddingValues(20.dp)){items(store.cards){c->MenuRow(c.name,c.items.firstOrNull()?.text?:"No prompts yet",fg,muted){onCard(c.id)};Divider()};item{TextButton(onClick=onNew){Text("+ New card",color=fg)}}}}
}

@Composable private fun EditCardScreen(store:KitStore,id:String,fg:Color,muted:Color,onBack:()->Unit,onChanged:()->Unit){
    val c=store.card(id)?:return;var name by remember(c.name){mutableStateOf(c.name)};var newPrompt by remember{mutableStateOf("")};var imageError by remember{mutableStateOf(false)};val ctx=LocalContext.current;val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){runCatching{ctx.contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)};if(c.personImage==null)c.personImage=u.toString() else c.cardImage=u.toString();imageError=false;onChanged()}}
    val leave={ if(c.personImage==null && c.cardImage==null) imageError=true else onBack() }
    BackHandler{leave()}
    Column(Modifier.fillMaxSize()){Header(c.name,leave,fg);LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Field("Name",name,fg){name=it;c.name=it;onChanged()}}
        item{Text("Images",fontWeight=FontWeight.SemiBold,color=fg);Text("Person photo and card image are each optional; at least one is required.",color=muted,fontSize=13.sp);if(imageError) Text("Choose at least one image before leaving this card.",color=Color(0xFFD32F2F),fontSize=13.sp,modifier=Modifier.padding(top=6.dp));Spacer(Modifier.height(8.dp));OutlineButton("Choose / add image",fg){picker.launch(arrayOf("image/*"))}}
        item{Text("Contact actions (${c.actions.size}/4)",fontWeight=FontWeight.SemiBold,color=fg)}
        items(c.actions){a->Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(a.label,color=fg);Text(a.destination,color=muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)};TextButton(onClick={c.actions.remove(a);onChanged()}){Text("Remove")}}}
        if(c.actions.size<4) item{AddActionRow(c,fg,onChanged)}
        item{Text("Prompts & things",fontWeight=FontWeight.SemiBold,color=fg)}
        items(c.items){i->Row(verticalAlignment=Alignment.CenterVertically){Text(i.text,color=fg,modifier=Modifier.weight(1f));TextButton(onClick={c.items.remove(i);onChanged()}){Text("Remove")}}}
        item{Field("Add prompt / note / link",newPrompt,fg,true){newPrompt=it};Spacer(Modifier.height(8.dp));OutlineButton("Add",fg){if(newPrompt.isNotBlank()){c.items+=CardItem(UUID.randomUUID().toString(),"Prompt",newPrompt);newPrompt="";onChanged()}}}
        item{Text("Decks",fontWeight=FontWeight.SemiBold,color=fg);store.decks.forEach{d->val checked=d.cardIds.contains(c.id);Row(Modifier.fillMaxWidth().clickable{if(checked)d.cardIds.remove(c.id)else d.cardIds.add(c.id);onChanged()}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(checked,{v->if(v&&!checked)d.cardIds.add(c.id)else if(!v&&checked)d.cardIds.remove(c.id);onChanged()});Text(d.title,color=fg)}}}
    }}
}

@Composable private fun AddActionRow(card:KitCard,fg:Color,onChanged:()->Unit){var label by remember{mutableStateOf("")};var dest by remember{mutableStateOf("")};Column{Field("Action label",label,fg){label=it};Spacer(Modifier.height(8.dp));Field("Phone / URL / email",dest,fg){dest=it};Spacer(Modifier.height(8.dp));OutlineButton("Add contact action",fg){if(label.isNotBlank()&&dest.isNotBlank()&&card.actions.size<4){card.actions+=ContactAction(UUID.randomUUID().toString(),"Custom",label,normalizeDestination(dest));label="";dest="";onChanged()}}}}
private fun normalizeDestination(s:String):String { val t=s.trim();return when{t.startsWith("http")||t.startsWith("tel:")||t.startsWith("mailto:")->t;t.contains("@")&&!t.contains(" ")->"mailto:$t";t.filter{it.isDigit()}.length>=7->"tel:${t.filter{it.isDigit()||it=='+'}}";else->"https://$t"} }

@Composable private fun DealScreen(store:KitStore,deckId:String,cardId:String,fg:Color,muted:Color,onBack:()->Unit,onAnother:()->Unit,onFull:()->Unit,onChanged:()->Unit,onTrashed:()->Unit){
    val ctx=LocalContext.current;val deck=store.deck(deckId)?:return;val card=store.card(cardId)?:return;var expanded by remember{mutableStateOf(false)};var confirmTrash by remember{mutableStateOf(false)}
    BackHandler(enabled=expanded && !confirmTrash){expanded=false}
    Column(Modifier.fillMaxSize()){Header(deck.title,onBack,fg);LazyColumn(contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp)){
        item{Box(Modifier.fillMaxWidth().border(1.dp,if(fg==Color.White)Color(0xFF444444)else Color(0xFFDDDDDD),RoundedCornerShape(22.dp)).padding(22.dp)){Column{CardVisual(card);Spacer(Modifier.height(16.dp));Text(card.name,fontSize=29.sp,fontWeight=FontWeight.SemiBold,color=fg);Spacer(Modifier.height(18.dp));card.items.firstOrNull()?.let{Text(it.text,fontSize=18.sp,lineHeight=25.sp,color=fg)};Spacer(Modifier.height(22.dp));if(card.actions.isNotEmpty()){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){card.actions.take(4).forEach{a->PrimaryButton(a.label,fg){val prev=card.lastKitActionAt;card.lastKitActionAt=System.currentTimeMillis();onChanged();if(!launchExternal(ctx,a.destination)){card.lastKitActionAt=prev;onChanged()}}}}}else Text("No contact actions yet.",color=muted);Spacer(Modifier.height(18.dp));Box(Modifier.fillMaxWidth().clickable{expanded=!expanded}.padding(8.dp),contentAlignment=Alignment.Center){Text(if(expanded)"⌃" else "⌄",fontSize=28.sp,color=fg)};AnimatedVisibility(expanded){Column{Divider();Spacer(Modifier.height(12.dp));OutlineButton("Open full card",fg,onFull);Spacer(Modifier.height(8.dp));OutlineButton("Another card",fg,onAnother);Spacer(Modifier.height(18.dp));TextButton(onClick={confirmTrash=true},modifier=Modifier.fillMaxWidth()){Text("Trash from this deck",color=Color(0xFFD32F2F))}}}}}}
    }}
    if(confirmTrash) AlertDialog(onDismissRequest={confirmTrash=false},title={Text("Trash ${card.name} from ${deck.title}?")},text={Text("The card stays in KIT and in any other decks. You can add it back later from Cards.")},confirmButton={TextButton(onClick={deck.cardIds.remove(card.id);onChanged();confirmTrash=false;onTrashed()}){Text("Trash from this deck",color=Color(0xFFD32F2F))}},dismissButton={TextButton(onClick={confirmTrash=false}){Text("Cancel")}})
}
private fun launchExternal(ctx:android.content.Context,dest:String):Boolean {
    return runCatching {
        val i=Intent(Intent.ACTION_VIEW,Uri.parse(dest)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(i.resolveActivity(ctx.packageManager)==null) return false
        ctx.startActivity(i); true
    }.getOrDefault(false)
}

private fun selectCard(store:KitStore, deck:KitDeck, exclude:String?):KitCard? {
    val candidates=deck.cardIds.mapNotNull(store::card).filter{it.id!=exclude}.ifEmpty{deck.cardIds.mapNotNull(store::card)}
    if(candidates.isEmpty()) return null
    val now=System.currentTimeMillis(); val cadenceMs=deck.cadenceDays.coerceAtLeast(1)*86_400_000.0
    val weighted=candidates.map{c->
        val age=c.lastKitActionAt?.let{(now-it).coerceAtLeast(0).toDouble()} ?: cadenceMs*2
        val w=(age/cadenceMs).coerceIn(.15,4.0)
        c to w
    }
    val total=weighted.sumOf{it.second}; var r=Math.random()*total
    for((c,w) in weighted){r-=w;if(r<=0)return c};return weighted.last().first
}

@Composable private fun StoredImage(uri:String, modifier:Modifier=Modifier, scale:ContentScale=ContentScale.Crop){
    val ctx=LocalContext.current
    val bitmap=remember(uri){ runCatching{ctx.contentResolver.openInputStream(Uri.parse(uri))?.use{BitmapFactory.decodeStream(it)}}.getOrNull() }
    if(bitmap!=null) Image(bitmap.asImageBitmap(),contentDescription=null,modifier=modifier,contentScale=scale)
}

@Composable private fun CardVisual(card:KitCard){
    val uri=card.cardImage?.takeIf{it.startsWith("content:")||it.startsWith("file:")} ?: card.personImage?.takeIf{it.startsWith("content:")||it.startsWith("file:")}
    if(uri!=null) StoredImage(uri,Modifier.fillMaxWidth().height(210.dp),ContentScale.Crop)
}

@Composable private fun SettingsScreen(store:KitStore,fg:Color,muted:Color,onChanged:()->Unit){
    val ctx=LocalContext.current;var notifStatus by remember{mutableStateOf("")};val perm=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->notifStatus=if(granted)"Notifications enabled" else "Notification permission denied"}
    Column(Modifier.fillMaxSize()){Header("Settings",fg=fg);LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
        item{Text("Appearance",fontWeight=FontWeight.SemiBold,color=fg);Spacer(Modifier.height(8.dp));SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){Appearance.entries.forEachIndexed{i,a->SegmentedButton(selected=store.appearance==a,onClick={store.appearance=a;onChanged()},shape=SegmentedButtonDefaults.itemShape(i,Appearance.entries.size)){Text(a.name.lowercase().replaceFirstChar{it.uppercase()})}}}}
        item{Text("Notifications",fontWeight=FontWeight.SemiBold,color=fg);Text("Local suggestions only; no server or remote push.",color=muted,fontSize=13.sp);Spacer(Modifier.height(10.dp));OutlineButton("Schedule test in 15 seconds",fg){if(Build.VERSION.SDK_INT>=33)perm.launch(Manifest.permission.POST_NOTIFICATIONS);val req=OneTimeWorkRequestBuilder<NotificationWorker>().setInitialDelay(15,TimeUnit.SECONDS).build();WorkManager.getInstance(ctx).enqueue(req);notifStatus="Test scheduled"};if(notifStatus.isNotBlank())Text(notifStatus,color=muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))}
        item{OutlineButton("Reset prototype data",fg){store.reset();onChanged()}}
        item{Text("KIT Alpha 0.1",color=muted,fontSize=12.sp)}
    }}
}

@Composable private fun MenuRow(title:String,sub:String,fg:Color,muted:Color,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=18.sp,color=fg);if(sub.isNotBlank())Text(sub,color=muted,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)};Text("›",fontSize=28.sp,color=muted)}}
@Composable private fun Field(label:String,value:String,fg:Color,multiline:Boolean=false,onChange:(String)->Unit){Column{Text(label,color=fg.copy(alpha=.65f),fontSize=13.sp);Spacer(Modifier.height(5.dp));OutlinedTextField(value,onChange,modifier=Modifier.fillMaxWidth(),minLines=if(multiline)3 else 1,maxLines=if(multiline)5 else 1)}}
@Composable private fun PrimaryButton(label:String,fg:Color,onClick:()->Unit){Button(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=fg,contentColor=if(fg==Color.White)Color.Black else Color.White)){Text(label)}}
@Composable private fun OutlineButton(label:String,fg:Color,onClick:()->Unit){OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth(),border=BorderStroke(1.dp,fg)){Text(label,color=fg)}}
