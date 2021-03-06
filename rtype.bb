;******************************************************************************
; R TYPE
; 
; Side scrolling shoot'em up.
;
; Written in Blitz Basic 2.1 for Commodore Amiga 1200/4000 (AGA)
;
; (c) 2021 Stefano Coppi
;******************************************************************************

;******************************************************************************
; Compiler Options:
;
; Runtime error debugger: off (solo in release mode)
; Create Debug info for executable: off
; Bitmaps 20
; Shapes 600
;******************************************************************************

; allows launch from Workbench
WBStartup

; use WORD as default type for numeric variables (faster than QUICK)
DEFTYPE .w


;******************************************************************************
; CONSTANTS
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
#BITMAP_ENEMY03 = 7
#BITMAP_ENEMY04 = 8
#BITMAP_ENEMY05 = 9
#BITMAP_ENEMY06 = 10
#BITMAP_ENEMY08 = 12
#BITMAP_ENEMY09 = 13
#BITMAP_FIRE01  = 14
#BITMAP_EXPL01  = 15
#BITMAP_EXPL02  = 16
#BITMAP_ASSET_LD = 17

#PALETTE_MAIN = 0

#SHAPE_TILE = 0
#SHAPE_SHIP = 440
#SHAPE_ENEMY01 = 450
#SHAPE_ENEMY02 = 460
#SHAPE_ENEMY03 = 470
#SHAPE_ENEMY04 = 480
#SHAPE_ENEMY05 = 490
#SHAPE_ENEMY06 = 500
#SHAPE_ENEMY08 = 510
#SHAPE_ENEMY09 = 520
#SHAPE_FIRE01  = 530
#SHAPE_EXPL01  = 540
#SHAPE_EXPL02  = 550
#SHAPE_EXPL03  = 570

#QUEUE_ID = 0

#BACKGROUND_WIDTH = 704
#BACKGROUND_HEIGHT = 256
#FOREGROUND_CLIP_AREA_W = 48
#FOREGROUND_WIDTH = 320 + 2*#FOREGROUND_CLIP_AREA_W ; 384
#FOREGROUND_HEIGHT = 256

#BPP = 4

#SHIP_X0 = 64
#SHIP_Y0 = 88
#SHIP_ANIM_IDLE = 1
#SHIP_ANIM_UP   = 0
#SHIP_ANIM_DOWN = 2
#SHIP_SPEED = 2
#SHIP_STATE_ACTIVE       = 0
#SHIP_STATE_HIT          = 1
#SHIP_STATE_INVULNERABLE = 2

#WAVES_NUM = 13
#WAVE_PATH_LINEAR   = 0
#WAVE_PATH_SIN      = 1
#WAVE_PATH_CIRCLE   = 2

#MAX_ENEMIES = 8

#ALIENS_STATE_ACTIVE   = 0
#ALIENS_STATE_INACTIVE = 1
#ALIENS_STATE_HIT      = 2

#MAX_BULLETS = 5
#FIRE_DELAY  = 7
#BULLETS_SPEED = 4
#BULLET_STATE_INACTIVE = 0
#BULLET_STATE_ACTIVE   = 1



;******************************************************************************
; DATA STRUCTURES
;******************************************************************************

NEWTYPE .Vector2
    x.w
    y.w
End NEWTYPE

NEWTYPE .Ship
    x.w
    y.w
    animState.b
    state.b
    numFrames.b
    currFrame.b
    animDelay.w
    currDelay.w
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

; proiettile
NEWTYPE .Bullet
    x.w
    y.w
    state.b
    shapeID.w
End NEWTYPE


;******************************************************************************
; GLOBAL VARIABLES
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
Dim sinLUT.f(370)
Dim circularPath.Vector2(732)


db=0

Dim bullets.Bullet(#MAX_BULLETS)
fireDelay = 0


;******************************************************************************
; Procedures
;******************************************************************************

; inizializza i tiles usati per la mappa
Statement InitTiles{}
    ; bitmap contenente i tiles
    BitMap #BITMAP_TILES,320,352,4
    LoadBitMap #BITMAP_TILES,"level1_tiles.iff"

    Use BitMap #BITMAP_TILES
    ; crea una shape per ogni tile
    i = #SHAPE_TILE
    For y=0 To 351 Step 16
        For x=0 To 319 Step 16
            GetaShape i,x,y,16,16
            i = i+1
        Next x
    Next y
    
    Free BitMap #BITMAP_TILES
End Statement


; carica la grafica per l'expl03 (esplosione del player ship)
Statement LoadExpl03Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ASSET_LD,224,30,4
    LoadBitMap #BITMAP_ASSET_LD,"expl03.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_EXPL03
    For x=0 To 223 Step 32
        GetaShape i,x,0,32,30
        i = i+1
    Next

    Free BitMap #BITMAP_ASSET_LD
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
    myShip\state = #SHIP_STATE_ACTIVE
    myShip\numFrames = 7    ; explosion frames
    myShip\currFrame = 0
    myShip\animDelay = 5
    myShip\currDelay = 0

    LoadExpl03Gfx{}
End Statement


; inizializza la look up table del sin, usata per il path degli alieni
Statement InitSinLUT{}
    Shared sinLUT()

    For x=0 To 369
        s.f = Sin(x*Pi/180)
        sinLUT(x) = s
    Next
End Statement


; inizializza la look up table con le coordinate del path circolare degli alieni
Statement InitCircularPath{}
    Shared circularPath()

    ; ingresso nello schermo
    For i=0 To 97
        circularPath(i)\x = 369-i
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
    ; crea una shape per ogni frame
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

    Free BitMap #BITMAP_ENEMY02
End Statement


; carica la grafica di Enemy03
Statement LoadEnemy03Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY03,32,22,4
    LoadBitMap #BITMAP_ENEMY03,"enemy03.iff"

    Use BitMap #BITMAP_ENEMY03
    GetaShape #SHAPE_ENEMY03,0,0,32,22
    Free BitMap #BITMAP_ENEMY03
End Statement


; carica la grafica di Enemy04
Statement LoadEnemy04Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY04,128,26,4
    LoadBitMap #BITMAP_ENEMY04,"enemy04.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_ENEMY04
    For x=0 To 127 Step 32
        GetaShape i,x,0,32,26
        i = i+1
    Next

    Free BitMap #BITMAP_ENEMY04
End Statement


; carica la grafica di Enemy05
Statement LoadEnemy05Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY05,96,26,4
    LoadBitMap #BITMAP_ENEMY05,"enemy05.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_ENEMY05
    For x=0 To 95 Step 32
        GetaShape i,x,0,32,26
        i = i+1
    Next

    Free BitMap #BITMAP_ENEMY05
End Statement


; carica la grafica di Enemy06
Statement LoadEnemy06Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY06,192,26,4
    LoadBitMap #BITMAP_ENEMY06,"enemy07.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_ENEMY06
    For x=0 To 191 Step 32
        GetaShape i,x,0,32,26
        i = i+1
    Next

    Free BitMap #BITMAP_ENEMY06
End Statement


; carica la grafica di Enemy08 (Robot arancione)
Statement LoadEnemy08Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY08,240,43,4
    LoadBitMap #BITMAP_ENEMY08,"enemy08.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_ENEMY08
    For x=0 To 239 Step 48
        GetaShape i,x,0,48,43
        i = i+1
    Next

    Free BitMap #BITMAP_ENEMY08
End Statement


; carica la grafica di Enemy09 (serpentone circolare)
Statement LoadEnemy09Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_ENEMY09,256,27,4
    LoadBitMap #BITMAP_ENEMY09,"enemy09.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_ENEMY09
    For x=0 To 255 Step 32
        GetaShape i,x,0,32,27
        i = i+1
    Next

    Free BitMap #BITMAP_ENEMY09
End Statement


; carica la grafica per l'expl01 (esplosione degli alieni di grandezza normale)
Statement LoadExpl01Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_EXPL01,256,30,4
    LoadBitMap #BITMAP_EXPL01,"expl01.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_EXPL01
    For x=0 To 255 Step 32
        GetaShape i,x,0,32,30
        i = i+1
    Next

    Free BitMap #BITMAP_EXPL01
End Statement


; carica la grafica per l'expl02 (esplosione del robot)
Statement LoadExpl02Gfx{}

    ; bitmap contenente i frames
    BitMap #BITMAP_EXPL02,528,51,4
    LoadBitMap #BITMAP_EXPL02,"expl02.iff"

    ; crea una shape per ogni frame
    i=#SHAPE_EXPL02
    For x=0 To 527 Step 48
        GetaShape i,x,0,48,51
        i = i+1
    Next

    Free BitMap #BITMAP_EXPL02
End Statement


; carica la grafica degli alieni
Statement LoadAliensGfx{}
    LoadEnemy01Gfx{}
    LoadEnemy02Gfx{}
    LoadEnemy03Gfx{}
    LoadEnemy04Gfx{}
    LoadEnemy05Gfx{}
    LoadEnemy06Gfx{}
    LoadEnemy08Gfx{}
    LoadEnemy09Gfx{}
    LoadExpl01Gfx{}
    LoadExpl02Gfx{}
End Statement


; carica la grafica del bullet di base dello ship (fire01)
Statement LoadBullet01Gfx{}
    ; bitmap contenente i frames
    BitMap #BITMAP_FIRE01,17,4,4
    LoadBitMap #BITMAP_FIRE01,"fire01.iff"

    Use BitMap #BITMAP_FIRE01
    GetaShape #SHAPE_FIRE01,0,0,17,4
    Free BitMap #BITMAP_FIRE01
End Statement


; carica la grafica dei bullets
Statement LoadBulletsGfx{}
    LoadBullet01Gfx{}
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


; draw the player ship
Statement DrawShip{}
    Shared myShip,db

    Use BitMap #BITMAP_FOREGROUND+db
    
    If myShip\state = #SHIP_STATE_ACTIVE Then shapeID = #SHAPE_SHIP+myShip\animState
    If myShip\state = #SHIP_STATE_HIT Then shapeID = #SHAPE_EXPL03+myShip\currFrame

    QBlit #QUEUE_ID+db,shapeID,myShip\x,myShip\y
End Statement


; move and update the player ship
Statement MoveShip{}
    Shared myShip

    ; if ship is in Hit state, play the explosion animation
    If myShip\state = #SHIP_STATE_HIT
        myShip\currDelay = myShip\currDelay+1
        If myShip\currDelay = myShip\animDelay
            myShip\currDelay = 0
            myShip\currFrame = myShip\currFrame+1
            If myShip\currFrame = myShip\numFrames
                myShip\state = #SHIP_STATE_ACTIVE
                myShip\x = #SHIP_X0
                myShip\y = #SHIP_Y0
                myShip\animState = #SHIP_ANIM_IDLE
            EndIf
        EndIf
    Else
        myShip\x = myShip\x + Joyx(1)*#SHIP_SPEED
        myShip\y = myShip\y + Joyy(1)*#SHIP_SPEED

        myShip\x = QLimit( myShip\x,#FOREGROUND_CLIP_AREA_W,#FOREGROUND_CLIP_AREA_W+320-32)
        myShip\y = QLimit( myShip\y,0,176)

        myShip\animState = Joyy(1) + 1
    EndIf
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

    ; se non ?? stata ancora avviata una wave, cerca una wave con mapOffset = mapPointer
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
                aliens(j)\pause     = waves(i)\pause*j
                aliens(j)\pathOffset = 0
            Next
            
        EndIf
    Next
End Statement


; processa il movimento degli alieni della wave corrente
Statement ProcessAliens{}
    Shared aliens(),currentWaveNumEnemies,currentWavePathType,waveStarted,sinLUT(),currentWaveY
    Shared cosLUT(),sinLUT2(),circularPath()

    aliensInactiveCount = 0
    If waveStarted = True
        For i=0 To currentWaveNumEnemies -1
            If aliens(i)\state <> #ALIENS_STATE_INACTIVE
                ; attende in caso di pausa >0
                If aliens(i)\pause > 0
                    aliens(i)\pause = aliens(i)\pause - 1
                Else
                    ; animazione
                    aliens(i)\currDelay = aliens(i)\currDelay + 1
                    If aliens(i)\currDelay = aliens(i)\animDelay
                        aliens(i)\currDelay = 0
                        aliens(i)\currFrame = aliens(i)\currFrame + 1
                        If aliens(i)\currFrame = aliens(i)\numFrames 
                            If aliens(i)\state = #ALIENS_STATE_HIT
                                aliens(i)\state = #ALIENS_STATE_INACTIVE
                            Else
                                aliens(i)\currFrame = 0
                            EndIf
                        EndIf
                    EndIf

                    If aliens(i)\state = #ALIENS_STATE_ACTIVE
                        ; movimento
                        Select currentWavePathType
                            Case #WAVE_PATH_LINEAR
                                aliens(i)\x = aliens(i)\x - aliens(i)\speed
                            Case #WAVE_PATH_SIN
                                aliens(i)\x = aliens(i)\x - aliens(i)\speed
                                aliens(i)\x = QLimit(aliens(i)\x,0,369)
                                y.f = 30*sinLUT(aliens(i)\x)
                                aliens(i)\y = currentWaveY+y
                            Case #WAVE_PATH_CIRCLE
                                If aliens(i)\pathOffset <= 732
                                    aliens(i)\x = circularPath(aliens(i)\pathOffset)\x
                                    aliens(i)\y = circularPath(aliens(i)\pathOffset)\y
                                    aliens(i)\pathOffset = aliens(i)\pathOffset+1
                                EndIf
                        End Select

                        aliens(i)\x = QLimit(aliens(i)\x,0,369)
                        aliens(i)\y = QLimit(aliens(i)\y,0,160)
                    EndIf
                EndIf
                
                ; se x=0 allora cambia lo stato dell'alieno in inattivo
                If aliens(i)\x = 0
                    aliens(i)\state = #ALIENS_STATE_INACTIVE
                EndIf
            Else
                aliensInactiveCount = aliensInactiveCount + 1
            EndIf
        Next

        ; condizione di fine wave, che consente di avviare una nuova wave
        If (aliensInactiveCount = currentWaveNumEnemies) And (currentWaveNumEnemies > 0)
            waveStarted = False  
        EndIf
    EndIf
End Statement


; disegna i nemici
Statement DrawAliens{}
    Shared aliens(),currentWaveNumEnemies,db

    Use BitMap #BITMAP_FOREGROUND+db

    For i=0 To currentWaveNumEnemies-1
        If aliens(i)\state <> #ALIENS_STATE_INACTIVE 
            QBlit #QUEUE_ID+db,aliens(i)\shapeID+aliens(i)\currFrame,aliens(i)\x,aliens(i)\y
        EndIf
    Next

End Statement


; controlla la pressione del tasto fire del joystick e spara i bullets
Statement CheckFire{}
    Shared fireDelay,myShip,bullets()

    ; incrementa delay tra due emissioni di bullets
    fireDelay = fireDelay + 1

    ; se ?? stato premuto il tasto fire del joystick in porta 1
    If Joyb(1) = 1
        ; se ?? trascorso il FIRE_DELAY
        If fireDelay >= #FIRE_DELAY
            ; azzera il delay
            fireDelay = 0
            ; cerca l'ultimo bullet inattivo nell'array bullets
            inactiveIndex = -1
            For i=0 To #MAX_BULLETS-1
                If bullets(i)\state = #BULLET_STATE_INACTIVE Then inactiveIndex = i
            Next
            ; se c'?? un bullet inattivo
            If inactiveIndex>-1
                ; crea un bullet attivo alla posizione dello ship
                bullets(inactiveIndex)\state = #BULLET_STATE_ACTIVE
                bullets(inactiveIndex)\shapeID = #SHAPE_FIRE01
                bullets(inactiveIndex)\x = myShip\x + 32 - 6
                bullets(inactiveIndex)\y = myShip\y + 6 + 2
            EndIf
        EndIf
    EndIf
End Statement


; disegna i bullets
Statement DrawBullets{}
    Shared bullets(),db

    ; seleziona la bitmap su cui disegnare
    Use BitMap #BITMAP_FOREGROUND+db

    ; cicla sull'array bullets
    For i=0 To #MAX_BULLETS-1
        ; se il bullet corrente ?? attivo
        If bullets(i)\state = #BULLET_STATE_ACTIVE
            ; disegna il bullet
            QBlit #QUEUE_ID+db,#SHAPE_FIRE01,bullets(i)\x,bullets(i)\y
        EndIf
    Next
End Statement


; sposta i bullets e aggiorna lo stato
Statement MoveBullets{}
    Shared bullets()

    ; cicla sull'array bullets
    For i=0 To #MAX_BULLETS-1
        ; se il bullet corrente ?? attivo
        If bullets(i)\state = #BULLET_STATE_ACTIVE
            ; somma alla x la velocit??
            bullets(i)\x = bullets(i)\x + #BULLETS_SPEED
            ; se x ?? fuori schermo
            If bullets(i)\x >= (48+320)
                ; rende il bullet inattivo
                bullets(i)\state = #BULLET_STATE_INACTIVE
            EndIf
        EndIf
    Next
End Statement


; controlla le collisioni tra bullets e aliens
Statement CheckCollBulletsAliens{}
    Shared bullets(),currentWaveNumEnemies,aliens()

    ; ciclo sui bullets
    For i=0 To #MAX_BULLETS-1
        ; se il bullet corrente ?? attivo
        If bullets(i)\state = #BULLET_STATE_ACTIVE
            ; ciclo sugli alieni
            For j=0 To currentWaveNumEnemies-1
                ; se l'alieno corrente ?? attivo
                If aliens(j)\state = #ALIENS_STATE_ACTIVE
                     ; se c'?? collisione tra bullet ed alieno correnti
                    If ShapesHit(bullets(i)\shapeID,bullets(i)\x,bullets(i)\y,aliens(j)\shapeID,aliens(j)\x,aliens(j)\y) = True
                        ; rende inattivo il bullet
                        bullets(i)\state = #BULLET_STATE_INACTIVE
                        ; cambia lo stato dell'alieno in colpito
                        aliens(j)\state = #ALIENS_STATE_HIT
                        ; inizializza l'animazione dell'esplosione
                        ; per il robot arancione usa l'expl02 che ?? pi?? grande
                        If aliens(j)\shapeID = #SHAPE_ENEMY08
                            shapeID = #SHAPE_EXPL02
                        Else
                            shapeID = #SHAPE_EXPL01
                        EndIf
                        aliens(j)\shapeID   = shapeID
                        aliens(j)\numFrames = 8
                        aliens(j)\currFrame = 0
                        aliens(j)\currDelay = 0
                        aliens(j)\animDelay = 5
                        ; incrementa il punteggio    
                    EndIf
                EndIf
            Next
        EndIf
    Next
End Statement


; Check collisions between player ships and enemies
Statement CheckCollShipAliens{}
    Shared aliens(),currentWaveNumEnemies,myShip

    ; loops through all the aliens
    For i=0 To currentWaveNumEnemies-1
        ; only active aliens can collide with ship
        If aliens(i)\state = #ALIENS_STATE_ACTIVE
            ; if player ship collides with the current alien
            If ShapesHit(#SHAPE_SHIP,myShip\x,myShip\y,aliens(i)\shapeID,aliens(i)\x,aliens(i)\y) = True
                ; if ship state is not Hit, change player ship state to "hit"
                If myShip\state <> #SHIP_STATE_HIT
                    myShip\state = #SHIP_STATE_HIT
                    ; reset explosion frame and animation delay
                    myShip\currFrame = 0
                    myShip\currDelay = 0
                    ; decrease lives number
                EndIf
            EndIf
        EndIf
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
LoadBulletsGfx{}
InitializePalette{}
InitCopper{}
InitMap{}


;******************************************************************************
; MAIN LOOP
;******************************************************************************
; repeats main loop until ESC key is pressed
Repeat
    VWait
    DisplayBitMap #COPPERLIST_MAIN,#BITMAP_BACKGROUND,scrollX+fineScroll,0,#BITMAP_FOREGROUND+db,#FOREGROUND_CLIP_AREA_W,0
    db=1-db
    
    ScrollMap{}
    
    UnQueue (#QUEUE_ID+db)

    MoveShip{}
    CheckFire{}
    DrawShip{}

    MoveBullets{}
    DrawBullets{}

    StartNewWave{}
    ProcessAliens{}
    DrawAliens{}

    CheckCollBulletsAliens{}
    CheckCollShipAliens{}
    
Until  RawStatus(#KEY_ESC) = True


;******************************************************************************
; DATA
;******************************************************************************

; level 1 map
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
Data  369,40                ; x,y
Data  8                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,19                 ; width,height
Data  450                   ; shapeID #SHAPE_ENEMY01
Data  22                    ; mapOffset
Data  40                    ; pause
Data  30                    ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR
; wave 1 - enemy02
Data  6                     ; numEnemies
Data  369,80                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,21                 ; width,height
Data  460                   ; shapeID #SHAPE_ENEMY02
Data  40                    ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN
; wave 2 - enemy04
Data  1                     ; numEnemies
Data  369,150               ; x,y
Data  4                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,26                 ; width,height
Data  480                   ; shapeID #SHAPE_ENEMY04
Data  57                    ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR
; wave 3 - enemy02
Data  6                     ; numEnemies
Data  369,80                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,21                 ; width,height
Data  460                   ; shapeID #SHAPE_ENEMY02
Data  70                    ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN
; wave 4 - enemy03
Data  1                     ; numEnemies
Data  369,80                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  2                     ; speed
Data  32,22                 ; width,height
Data  470                   ; shapeID #SHAPE_ENEMY03
Data  87                    ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN
; wave 5 - enemy06
Data  1                     ; numEnemies
Data  369,150               ; x,y
Data  6                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,26                 ; width,height
Data  500                   ; shapeID #SHAPE_ENEMY06
Data  96                    ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR
; wave 6 - enemy03
Data  1                     ; numEnemies
Data  369,95                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,22                 ; width,height
Data  470                   ; shapeID #SHAPE_ENEMY03
Data  109                   ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN
; wave 7 - enemy05
Data  4                     ; numEnemies
Data  369,30                ; x,y
Data  3                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,26                 ; width,height
Data  490                   ; shapeID #SHAPE_ENEMY05
Data  123                   ; mapOffset
Data  40                    ; pause
Data  30                    ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR
; wave 8 - enemy05
Data  3                     ; numEnemies
Data  369,50                ; x,y
Data  3                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,26                 ; width,height
Data  490                   ; shapeID #SHAPE_ENEMY05
Data  139                   ; mapOffset
Data  40                    ; pause
Data  30                    ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR
; wave 9 - enemy09 (serpentone circolare)
Data  8                     ; numEnemies
Data  369,95                ; x,y
Data  8                     ; numFrames
Data  5                     ; animDelay
Data  1                     ; speed
Data  32,27                 ; width,height
Data  520                   ; shapeID #SHAPE_ENEMY09
Data  159                   ; mapOffset
Data  28                    ; pause
Data  0                     ; yoffset
Data  2                     ; pathType #WAVE_PATH_CIRCLE
; wave 10 - enemy02
Data  6                     ; numEnemies
Data  369,96                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,21                 ; width,height
Data  460                   ; shapeID #SHAPE_ENEMY02
Data  192                   ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN
; wave 11 - enemy03
Data  1                     ; numEnemies
Data  369,95                ; x,y
Data  1                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  32,22                 ; width,height
Data  470                   ; shapeID #SHAPE_ENEMY03
Data  217                   ; mapOffset
Data  30                    ; pause
Data  0                     ; yoffset
Data  1                     ; pathType WAVE_PATH_SIN
; wave 12 - enemy08 (Robot arancione)
Data  1                     ; numEnemies
Data  369,134               ; x,y
Data  5                     ; numFrames
Data  10                    ; animDelay
Data  1                     ; speed
Data  48,43                 ; width,height
Data  510                   ; shapeID #SHAPE_ENEMY08
Data  234                   ; mapOffset
Data  40                    ; pause
Data  30                    ; yoffset
Data  0                     ; pathType WAVE_PATH_LINEAR

End

