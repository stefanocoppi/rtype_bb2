;******************************************************************************
; R TYPE
; 
; Shoot'em up a scorrimento orizzontale.
;
; Scritto in Blitz Basic 2.1 per Amiga 1200 (AGA)
;
; (c) 2021 Stefano Coppi
;******************************************************************************

; consente l'esecuzione da Workbench
WBStartup

; usa WORD come tipo di default per i numeri (più veloce rispetto a QUICK)
DEFTYPE .w


;******************************************************************************
; COSTANTI
;******************************************************************************
#KEY_ESC = $45

#MAP_WIDTH  = 268
#MAP_HEIGHT = 12
#MAP_START  = 0

#COPPERLIST_MAIN = 0
#BITMAP_BACKGROUND = 0
#BITMAP_FOREGROUND = 1
#BITMAP_TILES = 3
#BITMAP_SHIP = 4
#BITMAP_ENEMY01 = 5
#BITMAP_ENEMY02 = 6

#PALETTE_MAIN = 0

#SHAPE_TILE = 0
#SHAPE_SHIP = 500
#SHAPE_ENEMY01 = 510
#SHAPE_ENEMY02 = 520

#QUEUE_ID = 0

#BACKGROUND_WIDTH = 704
#BACKGROUND_HEIGHT = 256
#FOREGROUND_WIDTH = 384
#FOREGROUND_HEIGHT = 256
#BPP = 4

#SHIP_X0 = 64
#SHIP_Y0 = 88
#SHIP_ANIM_IDLE = 1
#SHIP_ANIM_UP   = 0
#SHIP_ANIM_DOWN = 2
#SHIP_SPEED = 2

#WAVES_NUM = 2
#WAVE_PATH_LINEAR   = 0
#WAVE_PATH_SIN      = 1
#WAVE_PATH_CIRCLE   = 2

#MAX_ENEMIES = 8

#ALIENS_STATE_ACTIVE = 0
#ALIENS_STATE_INACTIVE = 1


;******************************************************************************
; TIPI DI DATO
;******************************************************************************

NEWTYPE .Vector2
    x.w
    y.w
End NEWTYPE

NEWTYPE .Ship
    x.w
    y.w
    animState.b
End NEWTYPE

NEWTYPE .Alien
    x.w
    y.w
    numFrames.b
    currFrame.b
    animDelay.w
    currDelay.w
    speed.w
    width.w
    height.w
    state.b
    pause.w
    shapeID.w
    pathOffset.w
End NEWTYPE

; formazione o wave di alieni
NEWTYPE .AlienWave
    numEnemies.b        
    alien.Alien         ; alieno usato nella wave
    mapOffset.w         ; offset nella mappa a cui deve comparire la wave
    pause.w             ; pausa tra la creazione di un alieno ed il successivo
    yoffset.w           ; distanza verticale tra gli alieni in caso di path lineare
    pathType.b          ; tipologia di path seguito dagli alieni
End NEWTYPE

;******************************************************************************
; VARIABILI
;******************************************************************************
Dim map(#MAP_WIDTH,#MAP_HEIGHT)
scrollX.q = 16.0
fineScroll.q = 0
newBlockRow = 0
mapPointer = 0
newBlockRightX = 352
newBlockLeftX = 0
DEFTYPE .Ship myShip

Dim waves.AlienWave(#WAVES_NUM)
Dim aliens.Alien(#MAX_ENEMIES)
currentWaveNumEnemies.b = 0
currentWavePathType.b = 0
waveStarted.b = False
currentWaveY.w = 0
Dim sinLUT.f(353)
Dim circularPath.Vector2(715)
aliensInactiveCount = 0

db=0

;******************************************************************************
; Procedure
;******************************************************************************

; inizializza i tiles usati per la mappa
Statement InitTiles{}
    ; bitmap contenente i tiles
    BitMap #BITMAP_TILES,320,352,4
    LoadBitMap #BITMAP_TILES,"level1_tiles.iff"

    Use BitMap #BITMAP_TILES
    ; crea una shape per ogni tile
    i=0
    For y=0 To 351 Step 16
        For x=0 To 319 Step 16
            GetaShape i,x,y,16,16
            i = i+1
        Next x
    Next y
    
    Free BitMap #BITMAP_TILES
End Statement


; inizializza lo ship
Statement InitShip{}
    Shared myShip

    ; bitmap contenente i tiles
    BitMap #BITMAP_SHIP,96,16,4
    LoadBitMap #BITMAP_SHIP,"ship.iff"

    Use BitMap #BITMAP_SHIP
    ; crea una shape per ogni tile
    i=#SHAPE_SHIP
    For x=0 To 95 Step 32
        GetaShape i,x,0,32,16
        i = i+1
    Next
    
    Free BitMap #BITMAP_SHIP

    myShip\x = #SHIP_X0
    myShip\y = #SHIP_Y0
    myShip\animState = #SHIP_ANIM_IDLE
End Statement


; inizializza la look up table del sin, usata per il path degli alieni
Statement InitSinLUT{}
    Shared sinLUT()

    For x=0 To 352
        s.f = Sin(x*Pi/180)
        sinLUT(x) = s
    Next
End Statement


; inizializza la look up table con le coordinate del path circolare degli alieni
Statement InitCircularPath{}
    Shared circularPath()

    ; ingresso nello schermo
    For i=0 To 80
        circularPath(i)\x = 352-i
        circularPath(i)\y = 84
    Next

    ; movimento circolare
    For x=0 To 360
        s.f = Sin(x*Pi/180)
        c.f = Cos(x*Pi/180)
        circularPath(i)\x = 32 + 160 + 80*c
        circularPath(i)\y = 84 - 80*s
        i = i+1
    Next

    ; uscita dallo schermo
    For x=272 To 0 Step -1
        circularPath(i)\x = x
        circularPath(i)\y = 84
        i = i+1
    Next
End Statement


; carica la grafica di Enemy01
Statement LoadEnemy01Gfx{}

    ; bitmap contenente i tiles
    BitMap #BITMAP_ENEMY01,256,19,4
    LoadBitMap #BITMAP_ENEMY01,"enemy01.iff"

    Use BitMap #BITMAP_ENEMY01
    ; crea una shape per ogni tile
    i=#SHAPE_ENEMY01
    For x=0 To 255 Step 32
        GetaShape i,x,0,32,19
        i = i+1
    Next
    
    Free BitMap #BITMAP_ENEMY01
End Statement


; carica la grafica di Enemy02
Statement LoadEnemy02Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY02,32,21,4
    LoadBitMap #BITMAP_ENEMY02,"enemy02.iff"

    Use BitMap #BITMAP_ENEMY02
    GetaShape #SHAPE_ENEMY02,0,0,32,21
    Free BitMap #BITMAP_ENEMY01
End Statement

; carica la grafica degli alieni
Statement LoadAliensGfx{}
    LoadEnemy01Gfx{}
    LoadEnemy02Gfx{}
End Statement


; inizializza e carica la palette
Statement InitializePalette{}
    InitPalette #PALETTE_MAIN,32
    LoadPalette #PALETTE_MAIN,"level1_tiles.iff",0
    LoadPalette #PALETTE_MAIN,"level1_tiles.iff",16
    ;AGAPalRGB #PALETTE_MAIN,16,0,0,0
End Statement


; inizializza la copperlist ed il display dello schermo
Statement InitCopper{}
    ; impostazioni della copperlist
    copperListType.l = $8                       ; 8 bitplanes
    copperListType = copperListType + $10       ; smooth scrolling
    copperListType = copperListType + $20       ; dual playfield
    copperListType = copperListType + $10000    ; AGA colors

    ; sfondo dello schermo
    BitMap #BITMAP_BACKGROUND,#BACKGROUND_WIDTH,#BACKGROUND_HEIGHT,#BPP

    ; foreground in cui vengono disegnati i bob dello ship, degli alieni e dei bullets (double buffered)
    BitMap #BITMAP_FOREGROUND,#FOREGROUND_WIDTH,#FOREGROUND_HEIGHT,#BPP   
    BitMap #BITMAP_FOREGROUND+1,#FOREGROUND_WIDTH,#FOREGROUND_HEIGHT,#BPP

    InitCopList #COPPERLIST_MAIN,44,256,copperListType,8,32,0
    
    DisplayPalette #COPPERLIST_MAIN,#PALETTE_MAIN

    ; Workaround per fixare gli indici della palette per il playfield 2 nella posizione corretta
    DisplayControls #COPPERLIST_MAIN,0,$1C00,0

    ; creiamo due code, a causa del double buffering
    Queue #QUEUE_ID,32
    Queue #QUEUE_ID+1,32

    BLITZ

    CreateDisplay #COPPERLIST_MAIN

    ; abilita lettura raw della tastiera
    BlitzKeys On
End Statement


; carica i dati della mappa in memoria
Statement LoadMapData{}
    Shared map()

    For y=0 To #MAP_HEIGHT-1
        For x=0 To #MAP_WIDTH-1
            Read tileIndex

            If (tileIndex And $F000) = $c000
                tileIndex=0
            Else
                tileIndex = tileIndex and $0FFF
            EndIf

            map(x,y) = tileIndex
        Next
    Next
End Statement

; inizializza la mappa
Statement InitMap{}
    Shared map(),mapPointer
    
    Use BitMap #BITMAP_BACKGROUND

    For y=0 To 11
        For x=0 To 20
            tileIndex = map(#MAP_START+x,y)
            Block (#SHAPE_TILE+tileIndex),16+x*16,y*16
        Next
    Next
    
    mapPointer = #MAP_START + 21
End Statement

; fa scorrere la mappa orizzontalmente a sx
Statement ScrollMap{}
    Shared map(),scrollX,fineScroll,mapPointer,newBlockRow,newBlockRightX,newBlockLeftX

    Use BitMap #BITMAP_BACKGROUND

    If mapPointer < #MAP_WIDTH
        
        fineScroll = fineScroll + 0.5

        If scrollX >= (320+32+16) Then scrollX = 16
        
        If newBlockRow<12
            tileIndex = map(mapPointer,newBlockRow)
            newBlockLeftX = scrollX-16
            newBlockRightX = scrollX + 320+16
            
            Block (#SHAPE_TILE+tileIndex),newBlockLeftX,newBlockRow*16
            Block (#SHAPE_TILE+tileIndex),newBlockRightX,newBlockRow*16

            newBlockRow = newBlockRow+1
        EndIf
        
        If fineScroll = 16
            scrollX = scrollX + 16
            fineScroll = 0
            newBlockRow = 0
            mapPointer = mapPointer + 1
        EndIf

    EndIf
End Statement

Statement DrawShip{}
    Shared myShip,db

    Use BitMap #BITMAP_FOREGROUND+db
    ;UnQueue #QUEUE_ID
    QBlit #QUEUE_ID+db,#SHAPE_SHIP+myShip\animState,myShip\x,myShip\y
End Statement

Statement MoveShip{}
    Shared myShip

    myShip\x = myShip\x + Joyx(1)*#SHIP_SPEED
    myShip\y = myShip\y + Joyy(1)*#SHIP_SPEED

    myShip\x = QLimit( myShip\x,32,320)
    myShip\y = QLimit( myShip\y,0,176)

    myShip\animState = Joyy(1) + 1
End Statement


; inizializza l'array delle waves, leggendo i dati
; NB: prima di chiamare questa procedura, usare Restore wavesData
; per posizionare correttamente il puntatore ai dati
Statement InitWaves{}
    Shared waves()
    
    For i=0 To #WAVES_NUM-1
        Read numEnemies
        waves(i)\numEnemies = numEnemies
        Read x,y
        waves(i)\alien\x = x
        waves(i)\alien\y = y
        Read numFrames
        waves(i)\alien\numFrames = numFrames
        waves(i)\alien\currFrame = 0
        Read animDelay
        waves(i)\alien\animDelay = animDelay
        waves(i)\alien\currDelay = 0
        Read speed 
        waves(i)\alien\speed = speed
        Read width,height
        waves(i)\alien\width = width
        waves(i)\alien\height = height
        waves(i)\alien\state = #ALIENS_STATE_ACTIVE
        Read shapeID
        waves(i)\alien\shapeID = shapeID
        Read mapOffset
        waves(i)\mapOffset = mapOffset
        Read pause
        waves(i)\pause = pause
        Read yoffset
        waves(i)\yoffset = yoffset
        Read pathType
        waves(i)\pathType = pathType
    Next
    
End Statement


; avvia una nuova wave di alieni
Statement StartNewWave{}
    Shared waves(),aliens(),mapPointer,currentWaveNumEnemies,currentWavePathType,waveStarted,currentWaveY

    ; se non è stata ancora avviata una wave, cerca una wave con mapOffset = mapPointer
    For i=0 To #WAVES_NUM-1
        If (waves(i)\mapOffset = mapPointer) And (waveStarted=False)
            waveStarted = True
            currentWaveNumEnemies = waves(i)\numEnemies
            currentWavePathType = waves(i)\pathType
            currentWaveY = waves(i)\alien\y

            ; inizializza l'array dei nemici
            For j=0 To waves(i)\numEnemies-1
                aliens(j)\x         = waves(i)\alien\x
                If waves(i)\pathType = #WAVE_PATH_LINEAR
                    aliens(j)\y     = waves(i)\alien\y+j*waves(i)\yoffset
                Else
                    aliens(j)\y     = waves(i)\alien\y
                EndIf
                aliens(j)\numFrames = waves(i)\alien\numFrames
                ;aliens(j)\currFrame = waves(i)\alien\currFrame
                aliens(j)\currFrame = Rnd(waves(i)\alien\numFrames-1)
                aliens(j)\animDelay = waves(i)\alien\animDelay
                aliens(j)\currDelay = waves(i)\alien\currDelay
                aliens(j)\speed     = waves(i)\alien\speed
                aliens(j)\width     = waves(i)\alien\width
                aliens(j)\height    = waves(i)\alien\height
                aliens(j)\state     = waves(i)\alien\state
                aliens(j)\shapeID   = waves(i)\alien\shapeID
                aliens(j)\pause     = waves(i)\pause*(j+1)
                aliens(j)\pathOffset = 0
            Next
            
        EndIf
    Next
End Statement


; processa il movimento degli alieni della wave corrente
Statement ProcessAliens{}
    Shared aliens(),currentWaveNumEnemies,currentWavePathType,waveStarted,sinLUT(),currentWaveY
    Shared cosLUT(),sinLUT2(),circularPath(),aliensInactiveCount

    If waveStarted = True
        For i=0 To currentWaveNumEnemies -1
            If aliens(i)\state = #ALIENS_STATE_ACTIVE
                ; attende in caso di pausa >0
                If aliens(i)\pause > 0
                    aliens(i)\pause = aliens(i)\pause - 1
                Else
                    ; animazione
                    aliens(i)\currDelay = aliens(i)\currDelay + 1
                    If aliens(i)\currDelay = aliens(i)\animDelay
                        aliens(i)\currDelay = 0
                        aliens(i)\currFrame = aliens(i)\currFrame + 1
                        If aliens(i)\currFrame = aliens(i)\numFrames Then aliens(i)\currFrame = 0
                    EndIf

                    ; movimento
                    Select currentWavePathType
                        Case #WAVE_PATH_LINEAR
                            aliens(i)\x = aliens(i)\x - aliens(i)\speed
                        Case #WAVE_PATH_SIN
                            aliens(i)\x = aliens(i)\x - aliens(i)\speed
                            y.f = 30*sinLUT(aliens(i)\x)
                            aliens(i)\y = currentWaveY+y
                        Case #WAVE_PATH_CIRCLE
                            If aliens(i)\pathOffset <= 715
                                aliens(i)\x = circularPath(aliens(i)\pathOffset)\x
                                aliens(i)\y = circularPath(aliens(i)\pathOffset)\y
                                aliens(i)\pathOffset = aliens(i)\pathOffset+1
                            EndIf
                    End Select

                    aliens(i)\x = QLimit(aliens(i)\x,0,352)
                    aliens(i)\y = QLimit(aliens(i)\y,0,160)
                EndIf
                
                ; se x=0 allora cambia lo stato dell'alieno in inattivo
                If aliens(i)\x = 0
                    aliens(i)\state = #ALIENS_STATE_INACTIVE
                    aliensInactiveCount = aliensInactiveCount + 1
                EndIf
            EndIf
        Next

        ; condizione di fine wave, che consente di avviare una nuova wave
        If (aliensInactiveCount = currentWaveNumEnemies) And (currentWaveNumEnemies > 0)
            waveStarted = False
            aliensInactiveCount = 0   
        EndIf
    EndIf
End Statement


; disegna i nemici
Statement DrawAliens{}
    Shared aliens(),currentWaveNumEnemies,db

    Use BitMap #BITMAP_FOREGROUND+db

    For i=0 To currentWaveNumEnemies-1
        QBlit #QUEUE_ID+db,aliens(i)\shapeID+aliens(i)\currFrame,aliens(i)\x,aliens(i)\y
    Next

End Statement


;******************************************************************************
; MAIN
;******************************************************************************
main:
Restore mapData
LoadMapData{}
Restore wavesData
InitWaves{}
InitSinLUT{}
InitCircularPath{}
InitTiles{}
InitShip{}
LoadAliensGfx{}
InitializePalette{}
InitCopper{}
InitMap{}


;******************************************************************************
; MAIN LOOP
;******************************************************************************
; ripete main loop finchè non viene premuto il tasto ESC
Repeat
    VWait
    DisplayBitMap #COPPERLIST_MAIN,#BITMAP_BACKGROUND,scrollX+fineScroll,0,#BITMAP_FOREGROUND+db,32,0
    db=1-db
    
    ScrollMap{}
    
    UnQueue (#QUEUE_ID+db)

    MoveShip{}
    DrawShip{}

    StartNewWave{}
    ProcessAliens{}
    DrawAliens{}

    
Until  RawStatus(#KEY_ESC) = True


;******************************************************************************
; DATI
;******************************************************************************

; mappa del livello 1
mapData:
; line 0
Data.w  $0000, $0000, $0000, $0000, $0000, $8000, $8000, $8000, $8000, $E000, $E000, $E000, $E000, $0000, $0000, $0000
Data.w  $0000, $0000, $0000, $0000, $0000, $0000, $0104, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000
Data.w  $0000, $0000, $0000, $0000, $0104, $0000, $0000, $0000, $0000, $0107, $0000, $0000, $0000, $0000, $0000, $0000
Data.w  $0000, $0105, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0107, $0000, $0000, $003E
Data.w  $0000, $0000, $0000, $0000, $0000, $0105, $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0104, $0000, $0000
Data.w  $0000, $0000, $0000, $0000, $0000, $003E, $0000, $0000, $0000, $0104, $0000, $0000, $0000, $0000, $0000, $0000
Data.w  $0000, $0000, $003F, $0000, $0000, $0104, $0000, $0000, $0000, $0000, $0000, $80C0, $80C1, $8141, $8142, $8143
Data.w  $8144, $8141, $8142, $8143, $8144, $8149, $814A, $814B, $814C, $8141, $8142, $8143, $8144, $8118, $8119, $811A
Data.w  $811B, $8108, $8109, $810A, $810B, $8141, $8142, $8143, $8144, $8149, $814A, $814B, $814C, $0000, $0000, $0000
Data.w  $0000, $8141, $8142, $8143, $8144, $813E, $813F, $8116, $8117, $014D, $014E, $014F, $80A0, $80A1, $80DA, $80DB
Data.w  $8145, $8146, $8147, $8148, $8141, $8142, $8143, $8144, $8118, $8119, $811A, $811B, $8145, $8146, $8147, $8148
Data.w  $8141, $8142, $8143, $8144, $8149, $814A, $814B, $814C, $0000, $0000, $0000, $0000, $8108, $8109, $810A, $810B
Data.w  $8141, $8142, $8143, $8144, $80E4, $80E5, $80E6, $80E7, $80E8, $80E9, $80EA, $80EB, $80EC, $80ED, $8141, $8142
Data.w  $8143, $8144, $8141, $8142, $8143, $8144, $8118, $8119, $811A, $811B, $8145, $8146, $8147, $8148, $8149, $814A
Data.w  $814B, $814C, $8145, $8146, $8147, $8148, $8141, $8142, $8143, $8144, $8118, $8119, $811A, $811B, $8108, $8109
Data.w  $810A, $810B, $8141, $8142, $8143, $8144, $8145, $8146, $8147, $8148, $8149, $814A, $814B, $814C, $8141, $8142
Data.w  $8143, $8144, $8149, $814A, $814B, $814C, $8141, $8142, $8143, $8144, $80C4, $80C5

; line 1
Data  00000,00000,00000,00000,00063,00000,00261,00000,00000,00000,00062,00000,00000,00000,00000,00260
Data  00000,00262,00000,00261,00000,00000,00000,00000,00000,00262,00000,00263,00000,00000,00000,00261
Data  00000,00261,00000,00000,00000,00000,00000,00000,00000,00000,00261,00000,00000,00000,00263,00000
Data  00000,00000,00000,00000,00000,00260,00000,00261,00000,00000,00261,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00063,00000,00000,00000,00261
Data  00000,00000,00000,00000,00000,00000,00000,00063,00000,00000,00063,00000,00000,00261,00000,00000
Data  00000,00000,00000,00000,00000,00000,00062,00000,00000,00000,00000,00212,32981,32982,32983,32984
Data  32985,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,33068,33069,33070
Data  33071,33052,33053,33054,33055,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,49360,00000,50080,00000,33106,33107,00298,00299,00000,00000,00000,00198,00199,33006,33007
Data  00000,00000,00000,00000,00000,00000,00000,00000,33068,33069,33070,33071,49296,00000,52416,00000
Data  49360,00000,49376,00000,50656,00000,50384,00000,00000,00000,00000,00000,33052,33053,33054,33055
Data  00000,00000,00000,00000,00248,33017,33018,33019,33020,33021,33022,33023,33024,33025,49328,00000
Data  50864,00000,50336,00000,50384,00000,33068,33069,33070,33071,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,33068,33069,33070,33071,33052,33053
Data  33054,33055,00000,00000,00000,00000,00000,00000,00000,00000,00000,00357,33126,33127,33120,33121
Data  33122,33123,33157,33158,33159,00392,32948,32949,32950,32951,32952,32953

; line 2
Data  00000,00000,00060,00000,00000,00000,00000,00063,00000,00261,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00260,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00062,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00062,00000,00000,00263,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00063,00000,00000,00000,00000,00262,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00261,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00261,00000,00000,00000,00261,00000,00000,00172,32941,32942,32943,32944
Data  32945,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,51872,00000,51872
Data  00000,33072,33073,33074,33075,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00336,00337,00000,00000,00000,00000,00000,00000,00000,00258,00259
Data  00000,00000,00000,00000,00000,00000,00000,00000,52048,00000,52368,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,33072,33073,33074,33075
Data  00000,00000,00000,00000,00268,33037,33038,33039,33040,33041,33042,33043,33044,33045,00000,00000
Data  00000,00000,00000,00000,00000,00000,51104,00000,51392,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,33072,33073
Data  33074,33075,00000,00000,00000,00000,00000,00000,00000,00000,00000,00377,00378,00379,33140,33141
Data  33142,33143,33177,33178,33179,00412,32968,32969,32970,32971,32972,32973

; line 3
Data  00000,00262,00000,00000,00263,00062,00000,00000,00000,00000,00000,00000,00000,00000,00263,00000
Data  00000,00000,00000,00263,00000,00000,00000,00000,00000,00000,00060,00000,00000,00062,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00061,00000,00000,00000,00063
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00062,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00261,00000,00000,00000,00000,00000,00000
Data  00261,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,32954,32955,32956,32957,32958
Data  32959,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,49312,00000,49344,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,51120,00000,51872,00000
Data  00000,00000,00000,00000,00288,33057,33058,33059,33060,33061,33062,33063,33064,33065,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,33124,33108,33109
Data  33110,33111,33188,33189,33190,00423,32988,32989,32990,32991,32992,32993

; line 4
Data  00000,00000,00000,00000,00000,00000,00000,00062,00000,00000,00063,00000,00261,00000,00000,00000
Data  00000,00260,00000,00000,00261,00260,00000,00000,00261,00000,00000,00000,00000,00000,00000,00000
Data  00000,00062,00000,00000,00000,00263,00000,00062,00000,00000,00000,00000,00000,00062,00000,00000
Data  00000,00000,00000,00261,00000,00000,00000,00062,00000,00000,00000,00000,00000,00261,00000,00000
Data  00000,00000,00000,00062,00000,00000,00000,00063,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00062,00000,00000,00000,00000,00261,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00063,00000,00000,00000,00000,32974,32975,32976,32977,32978
Data  00211,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,33076,33077,33078,33079,33080,33081,33082,00315,00316,00317,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00376,00360,00361
Data  33130,33131,33200,33201,33202,33203,33008,33009,33010,33011,33012,33013

; line 5
Data  00000,00000,00261,00000,00061,00000,00000,00000,00000,00000,00000,00000,00000,00000,00262,00000
Data  00000,00000,00000,00000,00000,00000,00000,00263,00000,00000,00000,00000,00000,00000,00000,00261
Data  00000,00000,00000,00000,00000,00261,00000,00000,00000,00263,00000,00261,00000,00000,00261,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00261,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00063
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00062,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00388
Data  33148,33149,33192,33193,33194,32768,32768,32768,00000,00000,32768,00000

; line 6
Data  00263,00000,00000,00062,00000,00000,00063,00000,00000,00060,00000,00000,00262,00000,00000,00000
Data  00000,00262,00000,00000,00260,00000,00000,00000,00000,00000,00260,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00263,00000,00000,00000,00000,00000,00000,00263,00000,00261,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00262,00000,00000,00261,00000,00000,00000,00000,00000,00260
Data  00000,00000,00000,00063,00000,00000,00000,00000,00000,00060,00000,00000,00000,00000,00000,00063
Data  00000,00000,00261,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00400,33169,33204,33205,33206,33207,32768,32768,00000,00000,32768,00000

; line 7
Data  00000,00261,00000,00000,00000,00000,00000,00062,00000,00000,00000,00063,00000,00000,00261,00000
Data  00000,00263,00000,00000,00000,00000,00262,00000,00000,00000,00000,00000,00062,00000,00000,00000
Data  00263,00000,00062,00000,00000,00000,00000,00000,00000,00062,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00261,00000,00000,00000,00000,00000,00000,00000,00062,00000,00000,00000
Data  00000,00000,00000,00063,00000,00261,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00261,00000,00000,00000,00000,00000,00000,00000,00260,00000
Data  00000,00000,00062,00000,00000,00000,00261,00000,00000,00000,00000,32894,32895,32896,32897,32898
Data  00131,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,32796,32797,32798,32799,32800,32801,32802,00035,00036,00037,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  33154,33155,33196,33197,33198,33199,32848,32849,32850,32851,32852,32853

; line 8
Data  00000,00000,00263,00000,00262,00000,00261,00000,00063,00000,00000,00000,00000,00000,00000,00062
Data  00000,00000,00260,00000,00000,00000,00000,00000,00000,00000,00263,00000,00000,00000,00000,00000
Data  00000,00261,00000,00000,00000,00000,00261,00000,00000,00000,00000,00000,00261,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00261,00000,00000,00263,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00263,00000,00000,00061,00000,00261,00000
Data  00000,00000,00000,00000,00260,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,32914,32915,32916,32917,32918
Data  32919,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,51393,00000,49873,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,50657,00000,50145,00000
Data  00000,00000,00000,00000,00048,32817,32818,32819,32820,32821,32822,32823,32824,32825,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00406,33175,32922,32923,32924,32925,32868,32869,32870,32871,32872,32873

; line 9
Data  00000,00062,00000,00000,00000,00063,00000,00000,00000,00000,00062,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00061,00000,00263,00000,00260,00000,00000,00000,00000,32792,32793,32794
Data  32795,00000,00000,00260,00000,00000,00000,00000,00263,00000,00000,00000,00000,00000,00000,00261
Data  00000,00263,00000,00000,00062,00000,00000,00000,00000,00000,00000,00000,00260,00000,00000,00261
Data  00000,32792,32793,32794,32795,00000,00000,00062,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00062,00000,00000,00000,00000,00000,00000,00000,00000,00063,00000,00261,00000,00261,00000
Data  00263,00000,00000,00000,00000,00000,00063,00000,00261,00000,00000,00166,32935,32936,32937,32938
Data  32939,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,51729,00000,50865
Data  00000,32792,32793,32794,32795,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00016,00017,00000,00000,00000,00000,00000,00000,00000,00098,00099
Data  00000,00000,00000,00000,00000,00000,00000,00000,32792,32793,32794,32795,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,32792,32793,32794,32795
Data  00000,00000,00000,00000,00068,32837,32838,32839,32840,32841,32842,32843,32844,32845,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,32792,32793
Data  32794,32795,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00393
Data  00394,33163,33164,33165,33166,33167,32888,32889,32890,32891,32892,32893

; line 10
Data  00263,00000,00000,00062,00000,00000,00000,00062,00000,00000,00000,00261,00000,32788,32789,32790
Data  32791,00000,00261,00000,00000,00000,00000,00000,00000,00262,00000,00263,00000,32812,32813,32814
Data  32815,32788,32789,32790,32791,00000,00262,00000,00000,00000,00000,00260,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00260,00000,32788,32789,32790,32791,00263,00000,00000
Data  00000,32812,32813,32814,32815,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00260,00000,00000,00262,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,32788,32789,32790,32791,00000,00000,00000,00000,00000,00000,00132,32901,32902,32903,32904
Data  32905,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,32788,32789,32790
Data  32791,32812,32813,32814,32815,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,32786,32787,00058,00059,00000,00000,00000,00158,00159,32886,32887
Data  00000,00000,00000,00000,00000,00000,00000,00000,32812,32813,32814,32815,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,32788,32789,32790,32791,32812,32813,32814,32815
Data  00000,00000,00000,00000,00088,32857,32858,32859,32860,32861,32862,32863,32864,32865,00000,00000
Data  00000,00000,00000,00000,00000,00000,32788,32789,32790,32791,00000,00000,00000,00000,00000,00000
Data  00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,32788,32789,32790,32791,32812,32813
Data  32814,32815,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00413
Data  33182,33183,33184,33185,33186,33187,32908,32909,32910,32911,32912,32913

; line 11
Data  00000,00000,00260,00000,00000,00263,00000,00000,00000,00063,00000,00000,00000,32808,32809,32810
Data  32811,32773,32774,32775,32776,32769,32770,32771,32772,32773,32774,32775,32776,32832,32833,32834
Data  32835,32808,32809,32810,32811,32773,32774,32775,32776,32769,32770,32771,32772,32777,32778,32779
Data  32780,32777,32778,32779,32780,32773,32774,32775,32776,32808,32809,32810,32811,32773,32774,32775
Data  32776,32832,32833,32834,32835,32769,32770,32771,32772,32773,32774,32775,32776,32777,32778,32779
Data  32780,32777,32778,32779,32780,32769,32770,32771,32772,32777,32778,32779,32780,32773,32774,32775
Data  32776,32808,32809,32810,32811,32777,32778,32779,32780,00000,00000,32920,32921,32769,32770,32771
Data  32772,32769,32770,32771,32772,32777,32778,32779,32780,32769,32770,32771,32772,32808,32809,32810
Data  32811,32832,32833,32834,32835,32769,32770,32771,32772,32773,32774,32775,32776,32777,32778,32779
Data  32780,32769,32770,32771,32772,32806,32807,32846,32847,00013,00014,00015,32946,32947,32906,32907
Data  32773,32774,32775,32776,32777,32778,32779,32780,32832,32833,32834,32835,32769,32770,32771,32772
Data  32777,32778,32779,32780,32769,32770,32771,32772,32808,32809,32810,32811,32832,32833,32834,32835
Data  32769,32770,32771,32772,32876,32877,32878,32879,32880,32881,32882,32883,32884,32885,32769,32770
Data  32771,32772,32769,32770,32771,32772,32808,32809,32810,32811,32773,32774,32775,32776,32769,32770
Data  32771,32772,32773,32774,32775,32776,32769,32770,32771,32772,32808,32809,32810,32811,32832,32833
Data  32834,32835,32769,32770,32771,32772,32777,32778,32779,32780,32769,32770,32771,32772,32773,32774
Data  32775,32776,32777,32778,32779,32780,32769,32770,32771,32772,32932,32933

; wave di alieni
wavesData:
; wave 0 - enemy01
Data  4                     ; numEnemies
Data  352,40                ; x,y
Data  8                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,19                 ; width,height
Data  510                   ; shapeID #SHAPE_ENEMY01
Data  22                    ; mapOffset
Data  40                    ; pause
Data  30                    ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR
; wave 1 - enemy02
Data  6                     ; numEnemies
Data  352,80                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,21                 ; width,height
Data  520                   ; shapeID #SHAPE_ENEMY02
Data  40                    ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN

End

