PROGRAM = wlocd

INCLUDEDIRS = -I/usr/X11R6/include -I..

LIBDIRS = -L/usr/X11R6/lib -L/usr/lib

LIBS = -lwlocate

DBGFLAGS = -O2 -g0 -DNDEBUG
ifeq ($(DEBUG),1)
DBGFLAGS = -O0 -g3 -DNOSPLASH
endif

CXXSOURCES = main.cpp

CXXOBJECTS = $(CXXSOURCES:.cpp=.o)
CXXFLAGS = -Wall -DESRI_UNIX -DENV_LINUX $(INCLUDEDIRS) $(DBGFLAGS)
CXX = g++

LDFLAGS = $(LIBDIRS) $(LIBS)

all: $(PROGRAM)

$(PROGRAM): $(CXXOBJECTS)
	$(CXX) -o $@ $(CXXOBJECTS) $(LDFLAGS)
	strip $(PROGRAM)

clean:
	$(RM) -f $(CXXOBJECTS) $(PROGRAM)

